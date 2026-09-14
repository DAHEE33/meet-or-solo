package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import java.time.OffsetDateTime;
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

    /**
     * 둘 다 도착했으면 2인 진행에 동의하지 않았어도 유지한다({@code docs/19} 4.11.2).
     *
     * <p>"2명은 싫어요"는 만나기 전의 의사다. 도착 마감 시점에는 둘 다 현장에 나와 있고, 거기서
     * 그룹을 취소해도 만난 사실은 달라지지 않는다. 취소하면 끝까지 나온 두 사람만 보상을 잃는다.
     */
    @Test
    void 두명이_모두_도착했으면_최소인원_미동의여도_유지한다() {
        assertThat(policy.cancellationReason(List.of(arrived(false), arrived(false)))).isNull();
    }

    /** 한 명이라도 오지 않았다면 만남이 시작되지 않았으므로 사전 의사가 그대로 유효하다. */
    @Test
    void 한명만_도착했으면_최소인원_미동의로_취소한다() {
        assertThat(policy.cancellationReason(List.of(arrived(false), member(false))))
                .isEqualTo("MINIMUM_TWO_NOT_ALLOWED");
    }

    @Test
    void 한명_이하면_도착했어도_취소한다() {
        assertThat(policy.cancellationReason(List.of(arrived(true))))
                .isEqualTo("INSUFFICIENT_ACTIVE_MEMBERS");
    }

    private MatchGroupMember member(boolean allowMinimumTwo) {
        return member(allowMinimumTwo, "JOINED");
    }

    private MatchGroupMember arrived(boolean allowMinimumTwo) {
        return member(allowMinimumTwo, "ARRIVED");
    }

    private MatchGroupMember member(boolean allowMinimumTwo, String status) {
        MatchGroupMember member = mock(MatchGroupMember.class);
        when(member.getAllowMinimumTwo()).thenReturn(allowMinimumTwo);
        when(member.getStatus()).thenReturn(status);
        // 만남 성립 판정은 status가 아니라 arrived_at을 본다. 먼저 나간 사람도 도착자다.
        when(member.getArrivedAt()).thenReturn(
                "ARRIVED".equals(status) ? OffsetDateTime.parse("2026-07-27T12:40:00+09:00") : null);
        return member;
    }
}
