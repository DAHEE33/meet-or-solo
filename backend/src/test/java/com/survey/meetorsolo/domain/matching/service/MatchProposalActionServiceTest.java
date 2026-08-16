package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.matching.dto.MatchProposalActionRequest.Action;
import com.survey.meetorsolo.domain.matching.entity.MatchProposal;
import com.survey.meetorsolo.domain.matching.repository.MatchProposalRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MatchProposalActionServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-23T06:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final MatchProposalRepository proposals = mock(MatchProposalRepository.class);
    private final MatchProposalResponseService responses = mock(MatchProposalResponseService.class);
    private final MatchProposalActionService service =
            new MatchProposalActionService(CLOCK, proposals, responses);

    @Test
    void round1_ACCEPT와_REJECT를_기존_service_값으로_변환한다() {
        MatchProposal proposal = proposal(1L, 10L, MatchProposal.TYPE_INITIAL_MATCH);
        when(proposals.findByIdAndMemberId(1L, 10L)).thenReturn(Optional.of(proposal));
        when(responses.respond(1L, 10L, "ACCEPTED", java.time.OffsetDateTime.now(CLOCK)))
                .thenReturn(new MatchProposalResponseResult(3L, 1L, "ACCEPTED", "WAITING_RESPONSES"));

        var result = service.respond(10L, 1L, Action.ACCEPT);

        assertThat(result.recordedResponse()).isEqualTo("ACCEPTED");
        verify(responses).respond(1L, 10L, "ACCEPTED", java.time.OffsetDateTime.now(CLOCK));

        when(responses.respond(1L, 10L, "REJECTED", java.time.OffsetDateTime.now(CLOCK)))
                .thenReturn(new MatchProposalResponseResult(3L, 1L, "REJECTED", "FAILED"));
        assertThat(service.respond(10L, 1L, Action.REJECT).recordedResponse()).isEqualTo("REJECTED");
    }

    @Test
    void round2_ACCEPT와_CANCEL을_기존_service_값으로_변환한다() {
        MatchProposal proposal = proposal(2L, 10L, MatchProposal.TYPE_INSUFFICIENT_MEMBERS_CONFIRMATION);
        when(proposals.findByIdAndMemberId(2L, 10L)).thenReturn(Optional.of(proposal));
        when(responses.respond(2L, 10L, "START_WITH_CURRENT_MEMBERS", java.time.OffsetDateTime.now(CLOCK)))
                .thenReturn(new MatchProposalResponseResult(
                        3L, 2L, "START_WITH_CURRENT_MEMBERS", "INSUFFICIENT_MEMBERS"));
        when(responses.respond(2L, 10L, "CANCEL_CURRENT_MEMBERS", java.time.OffsetDateTime.now(CLOCK)))
                .thenReturn(new MatchProposalResponseResult(3L, 2L, "CANCEL_CURRENT_MEMBERS", "FAILED"));

        assertThat(service.respond(10L, 2L, Action.ACCEPT).recordedResponse())
                .isEqualTo("START_WITH_CURRENT_MEMBERS");
        assertThat(service.respond(10L, 2L, Action.CANCEL_CURRENT_MEMBERS).recordedResponse())
                .isEqualTo("CANCEL_CURRENT_MEMBERS");
    }

    @Test
    void proposal_유형과_맞지_않는_action은_400이다() {
        MatchProposal proposal = proposal(1L, 10L, MatchProposal.TYPE_INITIAL_MATCH);
        when(proposals.findByIdAndMemberId(1L, 10L))
                .thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> service.respond(10L, 1L, Action.CANCEL_CURRENT_MEMBERS))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MATCHING_INVALID_REQUEST));
    }

    @Test
    void 다른_회원의_proposal은_존재_여부를_숨기고_404이다() {
        when(proposals.findByIdAndMemberId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.respond(99L, 1L, Action.ACCEPT))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MATCHING_RESOURCE_NOT_FOUND));
    }

    @Test
    void 기존_응답을_변경하려는_충돌은_409이다() {
        MatchProposal proposal = proposal(1L, 10L, MatchProposal.TYPE_INITIAL_MATCH);
        when(proposals.findByIdAndMemberId(1L, 10L)).thenReturn(Optional.of(proposal));
        when(responses.respond(1L, 10L, "REJECTED", java.time.OffsetDateTime.now(CLOCK)))
                .thenThrow(new MatchProposalResponseException("기존 응답을 변경할 수 없습니다."));

        assertThatThrownBy(() -> service.respond(10L, 1L, Action.REJECT))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MATCHING_CONFLICT));
    }

    private MatchProposal proposal(long id, long memberId, String type) {
        MatchProposal proposal = mock(MatchProposal.class);
        when(proposal.getId()).thenReturn(id);
        when(proposal.getMemberId()).thenReturn(memberId);
        when(proposal.getProposalType()).thenReturn(type);
        return proposal;
    }
}
