package com.survey.meetorsolo.domain.matching.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MatchingScenarioFixtureTest {

    @Test
    void 후보_선정_fixture는_포함과_제외_조건을_함께_제공한다() {
        MatchingScenarioFixture.CandidateSelectionScenario scenario =
                MatchingScenarioFixture.candidateSelection();

        assertThat(scenario.expectedEligibleMemberIds())
                .containsExactlyInAnyOrder(9_110_001L, 9_110_002L);
        assertThat(scenario.candidates())
                .extracting(MatchingScenarioFixture.PoolCandidate::status)
                .contains("WAITING", "PROPOSED");
        assertThat(scenario.candidates())
                .anySatisfy(candidate -> assertThat(candidate.searchExpiresAt()).isBefore(scenario.now()));
        assertThat(scenario.candidates())
                .anySatisfy(candidate -> assertThat(candidate.festivalId()).isNotEqualTo(scenario.festivalId()));
        assertThat(scenario.blocks()).hasSize(1);
        assertThat(scenario.cooldowns()).hasSize(1);
    }

    @Test
    void 인원_미달_fixture는_같은_attempt의_새_proposal과_다음_round를_사용한다() {
        MatchingScenarioFixture.ProposalRoundScenario scenario =
                MatchingScenarioFixture.insufficientMembersProposalRound();

        assertThat(scenario.targetGroupSize()).isEqualTo(4);
        assertThat(scenario.acceptedMemberIds()).hasSize(2);
        assertThat(scenario.expectedNextRound()).isEqualTo(2);
        assertThat(scenario.proposals())
                .extracting(MatchingScenarioFixture.ProposalFixture::attemptId)
                .containsOnly(scenario.attemptId());
        assertThat(scenario.proposals())
                .filteredOn(proposal -> proposal.proposalRound() == 2)
                .extracting(MatchingScenarioFixture.ProposalFixture::proposalType)
                .containsOnly("INSUFFICIENT_MEMBERS_CONFIRMATION");

        Set<Long> proposalIds = new HashSet<>();
        assertThat(scenario.proposals()).allSatisfy(proposal ->
                assertThat(proposalIds.add(proposal.proposalId())).isTrue());
    }

    @Test
    void scheduler_fixture는_만료된_SENT만_timeout_기대값으로_표시한다() {
        MatchingScenarioFixture.SchedulerExpirationScenario scenario =
                MatchingScenarioFixture.schedulerExpiration();

        assertThat(scenario.expectedTimeoutProposalIds()).containsExactly(9_140_101L);
        assertThat(scenario.proposals())
                .filteredOn(proposal -> scenario.expectedTimeoutProposalIds().contains(proposal.proposalId()))
                .allSatisfy(proposal -> {
                    assertThat(proposal.status()).isEqualTo("SENT");
                    assertThat(proposal.expiresAt()).isBefore(scenario.now());
                });
    }

    @Test
    void SQL_seed는_test_classpath에만_있고_V10_제안_계약을_포함한다() throws IOException {
        try (var input = getClass().getResourceAsStream("/fixtures/matching-engine-foundation.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                    .contains("INSERT INTO match_pools")
                    .contains("INSERT INTO match_attempts")
                    .contains("INSERT INTO match_proposals")
                    .contains("'INITIAL_MATCH', 1")
                    .contains("'INSUFFICIENT_MEMBERS_CONFIRMATION', 2");
        }
    }
}
