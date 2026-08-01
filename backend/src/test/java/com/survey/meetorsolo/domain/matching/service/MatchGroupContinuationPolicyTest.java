package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchGroupContinuationPolicyTest {
    private final MatchGroupContinuationPolicy policy = new MatchGroupContinuationPolicy();

    @Test
    void 세명_이상이면_유지한다() {
        assertThat(policy.cancellationReason(List.of(member(false), member(false), member(false))))
                .isNull();
    }

    @Test
    void 두명_모두_최소인원을_허용하면_유지한다() {
        assertThat(policy.cancellationReason(List.of(member(true), member(true)))).isNull();
    }

    @Test
    void 두명_중_한명이라도_허용하지_않으면_취소한다() {
        assertThat(policy.cancellationReason(List.of(member(true), member(false))))
                .isEqualTo("MINIMUM_TWO_NOT_ALLOWED");
    }

    @Test
    void 한명_이하면_취소한다() {
        assertThat(policy.cancellationReason(List.of(member(true))))
                .isEqualTo("INSUFFICIENT_ACTIVE_MEMBERS");
    }

    private MatchGroupMember member(boolean allowMinimumTwo) {
        MatchGroupMember member = mock(MatchGroupMember.class);
        when(member.getAllowMinimumTwo()).thenReturn(allowMinimumTwo);
        return member;
    }
}
