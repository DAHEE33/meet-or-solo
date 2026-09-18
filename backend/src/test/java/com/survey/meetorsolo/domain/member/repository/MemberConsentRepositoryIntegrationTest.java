package com.survey.meetorsolo.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.member.dto.MemberConsentResponse;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.entity.MemberConsentType;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 동의 저장이 UNIQUE 제약, CHECK 제약과 실제로 맞물리는지 확인한다.
 *
 * <p>`ON CONFLICT` upsert와 `chk_member_consents_type`은 mock으로 검증할 수 없어 실제
 * PostgreSQL이 필요하다.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MemberConsentCommandRepository.class, MemberConsentQueryRepository.class})
@Testcontainers
class MemberConsentRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private MemberConsentCommandRepository commandRepository;

    @Autowired
    private MemberConsentQueryRepository queryRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long saveMember(String providerId) {
        Member member = memberRepository.save(
                Member.createKakaoMember(providerId, "테스트유저", null));
        entityManager.flush();
        return member.getId();
    }

    @Test
    void 동의를_기록하고_조회한다() {
        Long memberId = saveMember("consent-test-1");

        commandRepository.agree(memberId, MemberConsentType.AI_PROCESSING, SeoulDateTime.now());

        assertThat(queryRepository.hasAgreedConsent(memberId, "AI_PROCESSING")).isTrue();
        assertThat(queryRepository.hasAgreedConsent(memberId, "OVERSEAS_TRANSFER")).isFalse();
    }

    @Test
    void 같은_동의를_다시_기록해도_UNIQUE_제약을_위반하지_않는다() {
        Long memberId = saveMember("consent-test-2");
        OffsetDateTime first = SeoulDateTime.now();

        commandRepository.agree(memberId, MemberConsentType.AI_PROCESSING, first);
        commandRepository.agree(memberId, MemberConsentType.AI_PROCESSING, first.plusMinutes(1));

        List<MemberConsentResponse> found = queryRepository.findLatestByMemberIdAndTypes(
                memberId, List.of(MemberConsentType.AI_PROCESSING));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).agreed()).isTrue();
    }

    @Test
    void 철회하면_동의가_해제되고_재동의하면_다시_유효해진다() {
        Long memberId = saveMember("consent-test-3");
        commandRepository.agree(memberId, MemberConsentType.AI_PROCESSING, SeoulDateTime.now());

        boolean revoked = commandRepository.revoke(
                memberId, MemberConsentType.AI_PROCESSING, SeoulDateTime.now());

        assertThat(revoked).isTrue();
        assertThat(queryRepository.hasAgreedConsent(memberId, "AI_PROCESSING")).isFalse();

        List<MemberConsentResponse> afterRevoke = queryRepository.findLatestByMemberIdAndTypes(
                memberId, List.of(MemberConsentType.AI_PROCESSING));
        assertThat(afterRevoke.get(0).agreed()).isFalse();
        assertThat(afterRevoke.get(0).revokedAt()).isNotNull();

        // 재동의는 새 row가 아니라 기존 row 갱신이다. UNIQUE 제약 때문에 여기서 실패하면 안 된다.
        commandRepository.agree(memberId, MemberConsentType.AI_PROCESSING, SeoulDateTime.now());

        assertThat(queryRepository.hasAgreedConsent(memberId, "AI_PROCESSING")).isTrue();
        List<MemberConsentResponse> afterReAgree = queryRepository.findLatestByMemberIdAndTypes(
                memberId, List.of(MemberConsentType.AI_PROCESSING));
        assertThat(afterReAgree).hasSize(1);
        assertThat(afterReAgree.get(0).agreed()).isTrue();
        assertThat(afterReAgree.get(0).revokedAt()).isNull();
    }

    @Test
    void 철회할_동의가_없으면_아무것도_바꾸지_않는다() {
        Long memberId = saveMember("consent-test-4");

        boolean revoked = commandRepository.revoke(
                memberId, MemberConsentType.AI_PROCESSING, SeoulDateTime.now());

        assertThat(revoked).isFalse();
    }

    @Test
    void 여러_유형의_상태를_한_번에_조회한다() {
        Long memberId = saveMember("consent-test-5");
        commandRepository.agree(memberId, MemberConsentType.TERMS, SeoulDateTime.now());
        commandRepository.agree(memberId, MemberConsentType.PRIVACY, SeoulDateTime.now());
        commandRepository.agree(memberId, MemberConsentType.AI_PROCESSING, SeoulDateTime.now());
        commandRepository.revoke(memberId, MemberConsentType.AI_PROCESSING, SeoulDateTime.now());

        List<MemberConsentResponse> found = queryRepository.findLatestByMemberIdAndTypes(
                memberId, List.of(
                        MemberConsentType.TERMS,
                        MemberConsentType.PRIVACY,
                        MemberConsentType.AI_PROCESSING,
                        MemberConsentType.OVERSEAS_TRANSFER));

        assertThat(found).hasSize(3);
        assertThat(found).filteredOn(consent -> consent.agreed()).hasSize(2);
    }

    @Test
    void V11이_추가한_국외_이전_동의_유형을_저장할_수_있다() {
        // chk_member_consents_type CHECK 제약에 값이 실제로 들어 있는지 확인한다.
        Long memberId = saveMember("consent-test-6");

        commandRepository.agree(memberId, MemberConsentType.OVERSEAS_TRANSFER, SeoulDateTime.now());

        assertThat(queryRepository.hasAgreedConsent(memberId, "OVERSEAS_TRANSFER")).isTrue();
    }
}
