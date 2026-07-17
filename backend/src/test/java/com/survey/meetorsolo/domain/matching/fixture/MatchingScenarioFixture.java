package com.survey.meetorsolo.domain.matching.fixture;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * 실제 매칭 엔진 테스트에서 재사용할 결정적 시나리오 데이터입니다.
 * 운영 domain model을 대신하지 않으며 V1~V11 DB 계약의 입력과 기대 결과만 표현합니다.
 */
public final class MatchingScenarioFixture {

    public static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 17, 15, 0, 0, 0, ZoneOffset.ofHours(9));
    public static final long FESTIVAL_ID = 9_100_001L;

    private MatchingScenarioFixture() {
    }

    public static CandidateSelectionScenario candidateSelection() {
        List<PoolCandidate> candidates = List.of(
                pool(9_110_001L, 9_120_001L, FESTIVAL_ID, "WAITING", NOW.plusSeconds(60)),
                pool(9_110_002L, 9_120_002L, FESTIVAL_ID, "WAITING", NOW.plusSeconds(60)),
                pool(9_110_003L, 9_120_003L, FESTIVAL_ID, "WAITING", NOW.minusSeconds(1)),
                pool(9_110_004L, 9_120_004L, FESTIVAL_ID + 1, "WAITING", NOW.plusSeconds(60)),
                pool(9_110_005L, 9_120_005L, FESTIVAL_ID, "PROPOSED", NOW.plusSeconds(60)),
                pool(9_110_006L, 9_120_006L, FESTIVAL_ID, "WAITING", NOW.plusSeconds(60)),
                pool(9_110_007L, 9_120_007L, FESTIVAL_ID, "WAITING", NOW.plusSeconds(60))
        );

        return new CandidateSelectionScenario(
                NOW,
                FESTIVAL_ID,
                candidates,
                Set.of(new Block(9_110_001L, 9_110_006L)),
                Set.of(new Cooldown(9_110_007L, NOW.minusMinutes(1), NOW.plusMinutes(4))),
                Set.of(9_110_001L, 9_110_002L)
        );
    }

    public static ProposalRoundScenario insufficientMembersProposalRound() {
        return new ProposalRoundScenario(
                9_130_001L,
                4,
                List.of(
                        proposal(9_140_001L, 9_110_001L, "INITIAL_MATCH", 1, "ACCEPTED"),
                        proposal(9_140_002L, 9_110_002L, "INITIAL_MATCH", 1, "ACCEPTED"),
                        proposal(9_140_003L, 9_110_003L, "INITIAL_MATCH", 1, "REJECTED"),
                        proposal(9_140_004L, 9_110_004L, "INITIAL_MATCH", 1, "TIMEOUT"),
                        proposal(9_140_005L, 9_110_001L, "INSUFFICIENT_MEMBERS_CONFIRMATION", 2, "SENT"),
                        proposal(9_140_006L, 9_110_002L, "INSUFFICIENT_MEMBERS_CONFIRMATION", 2, "SENT")
                ),
                Set.of(9_110_001L, 9_110_002L),
                2
        );
    }

    public static SchedulerExpirationScenario schedulerExpiration() {
        return new SchedulerExpirationScenario(
                NOW,
                List.of(
                        new ExpirableProposal(9_140_101L, "SENT", NOW.minusSeconds(1)),
                        new ExpirableProposal(9_140_102L, "SENT", NOW.plusSeconds(1)),
                        new ExpirableProposal(9_140_103L, "ACCEPTED", NOW.minusSeconds(1))
                ),
                Set.of(9_140_101L)
        );
    }

    private static PoolCandidate pool(
            long memberId, long poolId, long festivalId, String status, OffsetDateTime searchExpiresAt) {
        return new PoolCandidate(memberId, poolId, festivalId, 4, true, status, NOW.minusSeconds(10), searchExpiresAt);
    }

    private static ProposalFixture proposal(
            long proposalId, long memberId, String type, int round, String status) {
        return new ProposalFixture(
                proposalId,
                9_130_001L,
                memberId,
                type,
                round,
                status,
                NOW.minusSeconds(10),
                NOW.plusSeconds(20)
        );
    }

    public record CandidateSelectionScenario(
            OffsetDateTime now,
            long festivalId,
            List<PoolCandidate> candidates,
            Set<Block> blocks,
            Set<Cooldown> cooldowns,
            Set<Long> expectedEligibleMemberIds
    ) {
    }

    public record PoolCandidate(
            long memberId,
            long poolId,
            long festivalId,
            int preferredGroupSize,
            boolean allowMinimumTwo,
            String status,
            OffsetDateTime enteredAt,
            OffsetDateTime searchExpiresAt
    ) {
    }

    public record Block(long blockerMemberId, long blockedMemberId) {
    }

    public record Cooldown(long memberId, OffsetDateTime startsAt, OffsetDateTime expiresAt) {
    }

    public record ProposalRoundScenario(
            long attemptId,
            int targetGroupSize,
            List<ProposalFixture> proposals,
            Set<Long> acceptedMemberIds,
            int expectedNextRound
    ) {
    }

    public record ProposalFixture(
            long proposalId,
            long attemptId,
            long memberId,
            String proposalType,
            int proposalRound,
            String status,
            OffsetDateTime sentAt,
            OffsetDateTime expiresAt
    ) {
    }

    public record SchedulerExpirationScenario(
            OffsetDateTime now,
            List<ExpirableProposal> proposals,
            Set<Long> expectedTimeoutProposalIds
    ) {
    }

    public record ExpirableProposal(long proposalId, String status, OffsetDateTime expiresAt) {
    }
}
