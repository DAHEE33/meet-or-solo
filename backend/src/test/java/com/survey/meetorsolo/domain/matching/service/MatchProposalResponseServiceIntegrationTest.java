package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.*;
import static com.survey.meetorsolo.domain.matching.fixture.MatchingScenarioFixture.NOW;

import com.survey.meetorsolo.domain.matching.group.MatchGroupCombination;
import com.survey.meetorsolo.domain.matching.group.MatchingCandidate;
import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties="spring.jpa.hibernate.ddl-auto=validate") @Testcontainers
@Sql(scripts={"/fixtures/matching-engine-cleanup.sql","/fixtures/matching-engine-foundation.sql"},
        config=@SqlConfig(transactionMode=SqlConfig.TransactionMode.ISOLATED))
class MatchProposalResponseServiceIntegrationTest {
    static final OffsetDateTime RESPONSE_AT=NOW.plusSeconds(10);
    static final String TOKEN="response-token";
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @Autowired MatchProposalCreationService creation; @Autowired MatchProposalResponseService service;
    @Autowired JdbcTemplate jdbc;

    @Test void 유효한_SENT_proposal을_수락하고_일부_수락이면_대기한다() {
        long attempt=prepare(3, NOW.plusMinutes(1)); long proposal=proposal(attempt, 9110002);
        var result=service.respond(proposal,9110002,"ACCEPTED",RESPONSE_AT);
        assertThat(result.attemptStatus()).isEqualTo("WAITING_RESPONSES");
        assertResponse(proposal,attempt,9110002,"ACCEPTED",RESPONSE_AT);
        assertThat(status("match_attempt_members",attempt,9110002)).isEqualTo("ACCEPTED");
    }

    @Test void 거절이_있어도_round1_전체_terminal까지_기다린_뒤_실패한다() {
        long attempt=prepare(3,NOW.plusMinutes(1));
        long accepted=proposal(attempt,9110002); service.respond(accepted,9110002,"ACCEPTED",RESPONSE_AT);
        long rejected=proposal(attempt,9110006); service.respond(rejected,9110006,"REJECTED",RESPONSE_AT.plusSeconds(1));
        assertThat(attemptStatus(attempt)).isEqualTo("WAITING_RESPONSES");
        service.respond(proposal(attempt,9110010),9110010,"REJECTED",RESPONSE_AT.plusSeconds(2));
        assertThat(attemptStatus(attempt)).isEqualTo("FAILED");
        assertThat(status("match_proposals",attempt,9110010)).isEqualTo("REJECTED");
        assertThat(status("match_attempt_members",attempt,9110010)).isEqualTo("REJECTED");
        assertPool(9120006,"CANCELLED",NOW.plusMinutes(1));
        assertPool(9120002,"WAITING",NOW.plusMinutes(1));
        assertPool(9120010,"CANCELLED",NOW.plusMinutes(1));
    }

    @Test void 비귀책_pool이_만료됐으면_EXPIRED이며_search_expires_at을_연장하지_않는다() {
        long attempt=prepare(2,NOW.plusSeconds(5)); long rejected=proposal(attempt,9110002);
        service.respond(rejected,9110002,"REJECTED",RESPONSE_AT);
        service.respond(proposal(attempt,9110006),9110006,"ACCEPTED",RESPONSE_AT);
        assertPool(9120002,"CANCELLED",NOW.plusSeconds(5));
        assertPool(9120006,"EXPIRED",NOW.plusSeconds(5));
    }

    @Test void expires_at과_같은_응답은_TIMEOUT으로_처리한다() {
        long attempt=prepare(2,NOW.plusMinutes(1)); long proposal=proposal(attempt,9110002);
        var result=service.respond(proposal,9110002,"ACCEPTED",NOW.plusSeconds(30));
        assertThat(result.response()).isEqualTo("TIMEOUT"); assertThat(attemptStatus(attempt)).isEqualTo("WAITING_RESPONSES");
        service.respond(proposal(attempt,9110006),9110006,"ACCEPTED",NOW.plusSeconds(29));
        assertThat(attemptStatus(attempt)).isEqualTo("FAILED");
        assertResponse(proposal,attempt,9110002,"TIMEOUT",NOW.plusSeconds(30));
        assertPool(9120002,"CANCELLED",NOW.plusMinutes(1)); assertPool(9120006,"WAITING",NOW.plusMinutes(1));
    }

    @Test void timeout_service는_만료_SENT를_처리하고_반복_실행은_멱등하다() {
        long attempt=prepare(2,NOW.plusMinutes(1));
        var first=service.timeoutAttempt(attempt,NOW.plusSeconds(30));
        var second=service.timeoutAttempt(attempt,NOW.plusSeconds(31));
        var third=service.timeoutAttempt(attempt,NOW.plusSeconds(32));
        assertThat(first.response()).isEqualTo("TIMEOUT"); assertThat(second.response()).isEqualTo("TIMEOUT");
        assertThat(third).isNull(); assertThat(count("match_responses","attempt_id",attempt)).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings={"ACCEPTED","REJECTED"})
    void 동일한_사용자_응답_반복은_기존_성공을_반환한다(String response) {
        long attempt=prepare(2,NOW.plusMinutes(1)); long proposal=proposal(attempt,9110002);
        var first=service.respond(proposal,9110002,response,RESPONSE_AT);
        var second=service.respond(proposal,9110002,response,RESPONSE_AT.plusSeconds(1));
        assertThat(second.response()).isEqualTo(first.response());
        assertThat(count("match_responses","proposal_id",proposal)).isOne();
    }

    @Test void ACCEPTED에서_REJECTED로_변경할_수_없다() {
        long attempt=prepare(3,NOW.plusMinutes(1)); long proposal=proposal(attempt,9110002);
        service.respond(proposal,9110002,"ACCEPTED",RESPONSE_AT);
        assertThatThrownBy(()->service.respond(proposal,9110002,"REJECTED",RESPONSE_AT.plusSeconds(1)))
                .isInstanceOf(MatchProposalResponseException.class).hasMessageContaining("변경");
    }

    @Test void REJECTED에서_ACCEPTED로_변경할_수_없다() {
        long attempt=prepare(2,NOW.plusMinutes(1)); long proposal=proposal(attempt,9110002);
        service.respond(proposal,9110002,"REJECTED",RESPONSE_AT);
        assertThatThrownBy(()->service.respond(proposal,9110002,"ACCEPTED",RESPONSE_AT.plusSeconds(1)))
                .isInstanceOf(MatchProposalResponseException.class).hasMessageContaining("변경");
    }

    @Test void TIMEOUT에서_사용자_응답으로_변경할_수_없다() {
        long attempt=prepare(2,NOW.plusMinutes(1)); long proposal=proposal(attempt,9110002);
        service.timeoutAttempt(attempt,NOW.plusSeconds(30));
        assertThatThrownBy(()->service.respond(proposal,9110002,"ACCEPTED",NOW.plusSeconds(31)))
                .isInstanceOf(MatchProposalResponseException.class).hasMessageContaining("변경");
    }

    @Test void proposal_member_소유자가_다르면_거부한다() {
        long attempt=prepare(2,NOW.plusMinutes(1)); long proposal=proposal(attempt,9110002);
        assertThatThrownBy(()->service.respond(proposal,9110006,"ACCEPTED",RESPONSE_AT))
                .isInstanceOf(MatchProposalResponseException.class).hasMessageContaining("소유");
    }

    @Test void 전원_수락은_group_member_pool_attempt를_원자_확정한다() {
        long attempt=prepare(3,NOW.plusMinutes(1));
        for(long member:List.of(9110002L,9110006L,9110010L)) service.respond(proposal(attempt,member),member,"ACCEPTED",RESPONSE_AT);
        assertThat(attemptStatus(attempt)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForMap("SELECT status,confirmed_member_count FROM match_groups WHERE attempt_id=?",attempt))
                .containsEntry("status","CONFIRMED").containsEntry("confirmed_member_count",3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_group_members gm JOIN match_groups g ON g.id=gm.group_id WHERE g.attempt_id=? AND gm.status='JOINED'",Integer.class,attempt)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_pools WHERE id IN (9120002,9120006,9120010) AND status='MATCHED'",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT confirmed_at FROM match_attempts WHERE id=?",OffsetDateTime.class,attempt)).isEqualTo(RESPONSE_AT);
    }

    @Test void 세명_목표에서_두명_수락하면_수락자에게만_round2를_생성한다() {
        long attempt=prepare(3,NOW.plusMinutes(1)); enableMinimumTwo(9120002L,9120006L);
        service.respond(proposal(attempt,9110002),9110002,"ACCEPTED",RESPONSE_AT);
        service.respond(proposal(attempt,9110006),9110006,"ACCEPTED",RESPONSE_AT);
        service.respond(proposal(attempt,9110010),9110010,"REJECTED",RESPONSE_AT.plusSeconds(1));

        assertThat(attemptStatus(attempt)).isEqualTo("INSUFFICIENT_MEMBERS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_proposals WHERE attempt_id=? AND proposal_round=2",Integer.class,attempt)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_proposals WHERE attempt_id=? AND proposal_round=2 AND proposal_type='INSUFFICIENT_MEMBERS_CONFIRMATION' AND status='SENT'",Integer.class,attempt)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_proposals WHERE attempt_id=? AND member_id=9110010 AND proposal_round=2",Integer.class,attempt)).isZero();
        assertPool(9120010,"CANCELLED",NOW.plusMinutes(1));
        assertThat(jdbc.queryForObject("SELECT expires_at FROM match_attempts WHERE id=?",OffsetDateTime.class,attempt))
                .isEqualTo(RESPONSE_AT.plusSeconds(31));
    }

    @Test void 네명_목표에서_두명이나_세명_수락도_round2에_진입한다() {
        long twoAccepted=prepare(List.of(9120002L,9120006L,9120010L,9120011L),NOW.plusMinutes(1));
        enableMinimumTwo(9120002L,9120006L);
        respondInitial(twoAccepted,List.of(9110002L,9110006L),List.of(9110010L,9110011L));
        assertThat(attemptStatus(twoAccepted)).isEqualTo("INSUFFICIENT_MEMBERS");
        assertThat(roundTwoCount(twoAccepted)).isEqualTo(2);

        resetFoundation();
        long threeAccepted=prepare(List.of(9120002L,9120006L,9120010L,9120011L),NOW.plusMinutes(1));
        enableMinimumTwo(9120002L,9120006L,9120010L);
        respondInitial(threeAccepted,List.of(9110002L,9110006L,9110010L),List.of(9110011L));
        assertThat(attemptStatus(threeAccepted)).isEqualTo("INSUFFICIENT_MEMBERS");
        assertThat(roundTwoCount(threeAccepted)).isEqualTo(3);
    }

    @Test void 수락자가_한명이거나_allowMinimumTwo_false를_포함하면_round2없이_실패한다() {
        long oneAccepted=prepare(3,NOW.plusMinutes(1)); enableMinimumTwo(9120002L);
        respondInitial(oneAccepted,List.of(9110002L),List.of(9110006L,9110010L));
        assertThat(attemptStatus(oneAccepted)).isEqualTo("FAILED"); assertThat(roundTwoCount(oneAccepted)).isZero();

        resetFoundation();
        long disallowed=prepare(3,NOW.plusMinutes(1)); enableMinimumTwo(9120002L);
        respondInitial(disallowed,List.of(9110002L,9110006L),List.of(9110010L));
        assertThat(attemptStatus(disallowed)).isEqualTo("FAILED"); assertThat(roundTwoCount(disallowed)).isZero();
    }

    @Test void round2_전원_진행_동의는_실제_2명으로_group을_확정한다() {
        long attempt=prepareRoundTwo();
        long first=proposal(attempt,9110002,2), second=proposal(attempt,9110006,2);
        service.respond(first,9110002,"START_WITH_CURRENT_MEMBERS",RESPONSE_AT.plusSeconds(2));
        assertThat(attemptStatus(attempt)).isEqualTo("INSUFFICIENT_MEMBERS");
        service.respond(second,9110006,"START_WITH_CURRENT_MEMBERS",RESPONSE_AT.plusSeconds(3));

        assertThat(attemptStatus(attempt)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForMap("SELECT status,confirmed_member_count FROM match_groups WHERE attempt_id=?",attempt))
                .containsEntry("status","CONFIRMED").containsEntry("confirmed_member_count",2);
        assertThat(count("match_groups","attempt_id",attempt)).isOne();
        assertPool(9120002,"MATCHED",NOW.plusMinutes(1)); assertPool(9120006,"MATCHED",NOW.plusMinutes(1));
        assertResponse(first,attempt,9110002,"START_WITH_CURRENT_MEMBERS",RESPONSE_AT.plusSeconds(2));
    }

    @Test void round2_취소는_취소자만_CANCELLED하고_비귀책_pool을_반환한다() {
        long attempt=prepareRoundTwo();
        service.respond(proposal(attempt,9110002,2),9110002,"START_WITH_CURRENT_MEMBERS",RESPONSE_AT.plusSeconds(2));
        service.respond(proposal(attempt,9110006,2),9110006,"CANCEL_CURRENT_MEMBERS",RESPONSE_AT.plusSeconds(3));
        assertThat(attemptStatus(attempt)).isEqualTo("FAILED");
        assertPool(9120006,"CANCELLED",NOW.plusMinutes(1)); assertPool(9120002,"WAITING",NOW.plusMinutes(1));
        assertThat(count("match_groups","attempt_id",attempt)).isZero();
    }

    @Test void round2_timeout은_취소와_같이_attempt를_실패시키며_재실행은_멱등하다() {
        long attempt=prepareRoundTwo();
        var result=service.timeoutAttempt(attempt,RESPONSE_AT.plusSeconds(31));
        assertThat(result.response()).isEqualTo("TIMEOUT"); assertThat(attemptStatus(attempt)).isEqualTo("FAILED");
        assertThat(service.timeoutAttempt(attempt,RESPONSE_AT.plusSeconds(32))).isNull();
        assertThat(count("match_responses","attempt_id",attempt)).isEqualTo(4);
    }

    @Test void round2_동일_응답은_멱등하고_응답_변경은_거부한다() {
        long attempt=prepareRoundTwo(); long proposal=proposal(attempt,9110002,2);
        var first=service.respond(proposal,9110002,"START_WITH_CURRENT_MEMBERS",RESPONSE_AT.plusSeconds(2));
        var second=service.respond(proposal,9110002,"START_WITH_CURRENT_MEMBERS",RESPONSE_AT.plusSeconds(3));
        assertThat(second.response()).isEqualTo(first.response()); assertThat(count("match_responses","proposal_id",proposal)).isOne();
        assertThatThrownBy(()->service.respond(proposal,9110002,"CANCEL_CURRENT_MEMBERS",RESPONSE_AT.plusSeconds(4)))
                .isInstanceOf(MatchProposalResponseException.class).hasMessageContaining("변경");
    }

    @Test void round2_마지막_두회원_동시_진행동의에도_group은_한개다() throws Exception {
        long attempt=prepareRoundTwo();
        runConcurrent(()->service.respond(proposal(attempt,9110002,2),9110002,"START_WITH_CURRENT_MEMBERS",RESPONSE_AT.plusSeconds(2)),
                ()->service.respond(proposal(attempt,9110006,2),9110006,"START_WITH_CURRENT_MEMBERS",RESPONSE_AT.plusSeconds(2)));
        assertThat(attemptStatus(attempt)).isEqualTo("CONFIRMED"); assertThat(count("match_groups","attempt_id",attempt)).isOne();
    }

    @Test void round2_진행동의와_취소_race는_실패_하나로_종료한다() throws Exception {
        long attempt=prepareRoundTwo();
        runConcurrentAllowingFailure(
                ()->service.respond(proposal(attempt,9110002,2),9110002,"START_WITH_CURRENT_MEMBERS",RESPONSE_AT.plusSeconds(2)),
                ()->service.respond(proposal(attempt,9110006,2),9110006,"CANCEL_CURRENT_MEMBERS",RESPONSE_AT.plusSeconds(2)));
        assertThat(attemptStatus(attempt)).isEqualTo("FAILED"); assertThat(count("match_groups","attempt_id",attempt)).isZero();
    }

    @Test void round2_생성중_DB실패는_마지막_round1응답까지_rollback한다() {
        long attempt=prepare(3,NOW.plusMinutes(1)); enableMinimumTwo(9120002L,9120006L);
        service.respond(proposal(attempt,9110002),9110002,"ACCEPTED",RESPONSE_AT);
        service.respond(proposal(attempt,9110006),9110006,"ACCEPTED",RESPONSE_AT);
        long last=proposal(attempt,9110010); String trigger="test_round_two_rollback";
        installFailureTrigger("match_proposals",trigger,"TG_OP='INSERT' AND NEW.proposal_round=2");
        try { assertThatThrownBy(()->service.respond(last,9110010,"REJECTED",RESPONSE_AT.plusSeconds(1)))
                .isInstanceOf(RuntimeException.class); }
        finally { dropFailureTrigger("match_proposals",trigger); }
        assertThat(attemptStatus(attempt)).isEqualTo("WAITING_RESPONSES");
        assertThat(status("match_proposals",attempt,9110010)).isEqualTo("SENT");
        assertThat(status("match_attempt_members",attempt,9110010)).isEqualTo("PROPOSED");
        assertThat(roundTwoCount(attempt)).isZero(); assertThat(count("match_responses","attempt_id",attempt)).isEqualTo(2);
    }

    @Test void 같은_proposal의_동시_동일_응답은_response_한건만_남긴다() throws Exception {
        long attempt=prepare(3,NOW.plusMinutes(1)); long proposal=proposal(attempt,9110002);
        runConcurrent(()->service.respond(proposal,9110002,"ACCEPTED",RESPONSE_AT),
                ()->service.respond(proposal,9110002,"ACCEPTED",RESPONSE_AT));
        assertThat(count("match_responses","proposal_id",proposal)).isOne();
    }

    @Test void 마지막_두_회원_동시_수락에도_group은_한개다() throws Exception {
        long attempt=prepare(3,NOW.plusMinutes(1)); service.respond(proposal(attempt,9110002),9110002,"ACCEPTED",RESPONSE_AT);
        runConcurrent(()->service.respond(proposal(attempt,9110006),9110006,"ACCEPTED",RESPONSE_AT),
                ()->service.respond(proposal(attempt,9110010),9110010,"ACCEPTED",RESPONSE_AT));
        assertThat(count("match_groups","attempt_id",attempt)).isOne(); assertThat(attemptStatus(attempt)).isEqualTo("CONFIRMED");
    }

    @Test void ACCEPTED와_TIMEOUT_race는_하나의_terminal결과만_남긴다() throws Exception {
        long attempt=prepare(2,NOW.plusMinutes(1)); long proposal=proposal(attempt,9110002);
        runConcurrentAllowingFailure(()->service.respond(proposal,9110002,"ACCEPTED",NOW.plusSeconds(29)),
                ()->service.timeoutAttempt(attempt,NOW.plusSeconds(30)));
        assertThat(count("match_responses","proposal_id",proposal)).isOne();
        assertThat(attemptStatus(attempt)).isIn("WAITING_RESPONSES","FAILED");
    }

    @Test void 같은_proposal의_ACCEPTED와_REJECTED_race는_응답_한건만_남긴다() throws Exception {
        long attempt=prepare(3,NOW.plusMinutes(1)); long proposal=proposal(attempt,9110002);
        runConcurrentAllowingFailure(()->service.respond(proposal,9110002,"ACCEPTED",RESPONSE_AT),
                ()->service.respond(proposal,9110002,"REJECTED",RESPONSE_AT));
        assertThat(count("match_responses","proposal_id",proposal)).isOne();
        assertThat(jdbc.queryForObject("SELECT response FROM match_responses WHERE proposal_id=?",String.class,proposal))
                .isIn("ACCEPTED","REJECTED");
    }

    @Test void REJECTED와_TIMEOUT_race는_중복응답없이_직렬화된다() throws Exception {
        long attempt=prepare(2,NOW.plusMinutes(1)); long proposal=proposal(attempt,9110002);
        runConcurrentAllowingFailure(()->service.respond(proposal,9110002,"REJECTED",NOW.plusSeconds(29)),
                ()->service.timeoutAttempt(attempt,NOW.plusSeconds(30)));
        assertThat(count("match_responses","proposal_id",proposal)).isOne();
        assertThat(attemptStatus(attempt)).isIn("WAITING_RESPONSES","FAILED");
    }

    @Test void 서로_다른_attempt의_응답은_독립적으로_완료된다() throws Exception {
        long first=prepare(List.of(9120002L,9120006L),NOW.plusMinutes(1));
        long second=prepare(List.of(9120010L,9120011L),NOW.plusMinutes(1));
        runConcurrent(()->service.respond(proposal(first,9110002),9110002,"REJECTED",RESPONSE_AT),
                ()->service.respond(proposal(second,9110010),9110010,"REJECTED",RESPONSE_AT));
        assertThat(attemptStatus(first)).isEqualTo("WAITING_RESPONSES"); assertThat(attemptStatus(second)).isEqualTo("WAITING_RESPONSES");
    }

    @Test void DB_unique_constraint가_한_proposal의_response_중복을_차단한다() {
        long attempt=prepare(3,NOW.plusMinutes(1)); long proposal=proposal(attempt,9110002);
        service.respond(proposal,9110002,"ACCEPTED",RESPONSE_AT);
        assertThatThrownBy(()->jdbc.update("INSERT INTO match_responses(proposal_id,attempt_id,member_id,response,responded_at) VALUES (?,?,?,?,?)",
                proposal,attempt,9110002,"ACCEPTED",RESPONSE_AT)).isInstanceOf(RuntimeException.class);
        assertThat(count("match_responses","proposal_id",proposal)).isOne();
    }

    @ParameterizedTest @ValueSource(strings={"match_responses","match_proposals","match_attempt_members","match_groups","match_group_members","match_pools","match_attempts"})
    void 확정_중간_DB실패는_마지막_응답과_전체_확정을_rollback한다(String table) {
        long attempt=prepare(2,NOW.plusMinutes(1)); long first=proposal(attempt,9110002), last=proposal(attempt,9110006);
        service.respond(first,9110002,"ACCEPTED",RESPONSE_AT);
        String trigger="test_response_rollback"; installFailureTrigger(table,trigger,triggerCondition(table));
        try { assertThatThrownBy(()->service.respond(last,9110006,"ACCEPTED",RESPONSE_AT)).isInstanceOf(RuntimeException.class); }
        finally { dropFailureTrigger(table,trigger); }
        assertThat(attemptStatus(attempt)).isEqualTo("WAITING_RESPONSES");
        assertThat(status("match_proposals",attempt,9110006)).isEqualTo("SENT");
        assertThat(status("match_attempt_members",attempt,9110006)).isEqualTo("PROPOSED");
        assertThat(count("match_responses","attempt_id",attempt)).isOne();
        assertThat(count("match_groups","attempt_id",attempt)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_pools WHERE id IN (9120002,9120006) AND status='PROPOSED'",Integer.class)).isEqualTo(2);
    }

    private long prepare(int size, OffsetDateTime searchExpiresAt) {
        return prepare(List.of(9120002L,9120006L,9120010L,9120011L).subList(0,size),searchExpiresAt);
    }
    private long prepare(List<Long> poolIds, OffsetDateTime searchExpiresAt) {
        int size=poolIds.size();
        for(long id:poolIds) jdbc.update("UPDATE match_pools SET preferred_group_size=?,status='LOCKED',locked_at=?,lock_token=?,search_expires_at=? WHERE id=?",size,NOW,TOKEN,searchExpiresAt,id);
        List<MatchingCandidate> candidates=IntStream.range(0,size).mapToObj(i->{long id=poolIds.get(i); return new MatchingCandidate(
                id,9110000L+(id-9120000L),9100001L,size,false,NOW.minusSeconds(i),List.of(TravelStyleCode.PHOTO));}).toList();
        return creation.createInitial(new MatchGroupCombination(candidates,new BigDecimal("100.00")),TOKEN,NOW,Duration.ofSeconds(30)).attemptId();
    }
    private long prepareRoundTwo() {
        long attempt=prepare(3,NOW.plusMinutes(1)); enableMinimumTwo(9120002L,9120006L);
        respondInitial(attempt,List.of(9110002L,9110006L),List.of(9110010L));
        return attempt;
    }
    private void respondInitial(long attempt,List<Long> accepted,List<Long> rejected) {
        for(long member:accepted) service.respond(proposal(attempt,member),member,"ACCEPTED",RESPONSE_AT);
        for(long member:rejected) service.respond(proposal(attempt,member),member,"REJECTED",RESPONSE_AT.plusSeconds(1));
    }
    private void enableMinimumTwo(Long... poolIds) {
        for(long poolId:poolIds) jdbc.update("UPDATE match_pools SET allow_minimum_two=true WHERE id=?",poolId);
    }
    private int roundTwoCount(long attempt) { return jdbc.queryForObject("SELECT count(*) FROM match_proposals WHERE attempt_id=? AND proposal_round=2",Integer.class,attempt); }
    private void resetFoundation() {
        jdbc.update("DELETE FROM match_responses"); jdbc.update("DELETE FROM match_group_members"); jdbc.update("DELETE FROM match_groups");
        jdbc.update("DELETE FROM match_proposals"); jdbc.update("DELETE FROM match_attempt_members"); jdbc.update("DELETE FROM match_attempts");
        jdbc.update("UPDATE match_pools SET preferred_group_size=3,allow_minimum_two=false,status='WAITING',locked_at=NULL,lock_token=NULL,search_expires_at=?",NOW.plusMinutes(1));
    }
    private long proposal(long attempt,long member) { return proposal(attempt,member,1); }
    private long proposal(long attempt,long member,int round) { return jdbc.queryForObject("SELECT id FROM match_proposals WHERE attempt_id=? AND member_id=? AND proposal_round=?",Long.class,attempt,member,round); }
    private String attemptStatus(long attempt) { return jdbc.queryForObject("SELECT status FROM match_attempts WHERE id=?",String.class,attempt); }
    private String status(String table,long attempt,long member) { return jdbc.queryForObject("SELECT status FROM "+table+" WHERE attempt_id=? AND member_id=?",String.class,attempt,member); }
    private int count(String table,String column,long id) { return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE "+column+"=?",Integer.class,id); }
    private void assertResponse(long proposal,long attempt,long member,String response,OffsetDateTime at) {
        var row=jdbc.queryForMap("SELECT proposal_id,attempt_id,member_id,response,responded_at FROM match_responses WHERE proposal_id=?",proposal);
        assertThat(row).containsEntry("proposal_id",proposal).containsEntry("attempt_id",attempt)
                .containsEntry("member_id",member).containsEntry("response",response);
        assertSameInstant(row.get("responded_at"),at);
    }
    private void assertPool(long id,String status,OffsetDateTime expiry) {
        var row=jdbc.queryForMap("SELECT status,search_expires_at,locked_at,lock_token FROM match_pools WHERE id=?",id);
        assertThat(row).containsEntry("status",status).containsEntry("locked_at",null).containsEntry("lock_token",null);
        assertSameInstant(row.get("search_expires_at"),expiry);
    }
    private void assertSameInstant(Object actual,OffsetDateTime expected) {
        assertThat(actual).isInstanceOf(Timestamp.class);
        assertThat(((Timestamp)actual).toInstant()).isEqualTo(expected.toInstant());
    }
    private void runConcurrent(Callable<?> left,Callable<?> right) throws Exception { var executor=Executors.newFixedThreadPool(2); try {
        var values=List.of(executor.submit(left),executor.submit(right)); for(var value:values)value.get(10,TimeUnit.SECONDS);
    } finally { executor.shutdownNow(); } }
    private void runConcurrentAllowingFailure(Callable<?> left,Callable<?> right) throws Exception { var executor=Executors.newFixedThreadPool(2); try {
        var values=List.of(executor.submit(left),executor.submit(right)); for(var value:values)try{value.get(10,TimeUnit.SECONDS);}catch(ExecutionException ignored){}
    } finally { executor.shutdownNow(); } }
    private String triggerCondition(String table) { return switch(table) {
        case "match_proposals" -> "TG_OP='UPDATE' AND NEW.status='ACCEPTED' AND NEW.member_id=9110006";
        case "match_attempt_members" -> "TG_OP='UPDATE' AND NEW.status='ACCEPTED' AND NEW.member_id=9110006";
        case "match_group_members" -> "TG_OP='INSERT' AND NEW.member_id=9110006";
        case "match_pools" -> "TG_OP='UPDATE' AND NEW.status='MATCHED'";
        case "match_attempts" -> "TG_OP='UPDATE' AND NEW.status='CONFIRMED'";
        default -> "TG_OP='INSERT'"; }; }
    private void installFailureTrigger(String table,String trigger,String condition) { jdbc.execute("CREATE OR REPLACE FUNCTION "+trigger+"_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF "+condition+" THEN RAISE EXCEPTION 'forced test failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT OR UPDATE ON "+table+" FOR EACH ROW EXECUTE FUNCTION "+trigger+"_fn()"); }
    private void dropFailureTrigger(String table,String trigger) { jdbc.execute("DROP TRIGGER IF EXISTS "+trigger+" ON "+table); jdbc.execute("DROP FUNCTION IF EXISTS "+trigger+"_fn()"); }
}
