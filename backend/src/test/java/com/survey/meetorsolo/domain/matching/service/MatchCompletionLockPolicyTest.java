package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.matching.entity.MatchGroup;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class MatchCompletionLockPolicyTest {

    private static final OffsetDateTime CONFIRMED_AT =
            OffsetDateTime.parse("2026-08-10T10:00:00+09:00");
    private final MatchCompletionLockPolicy policy = new MatchCompletionLockPolicy();

    @Test
    void 완료_group이_없으면_inactive다() {
        var lock = policy.evaluate(null, CONFIRMED_AT);

        assertThat(lock.active()).isFalse();
        assertThat(lock.groupId()).isNull();
        assertThat(lock.remainingSeconds()).isZero();
    }

    @Test
    void confirmedAt_1시간_전에는_active이고_정확한_남은_초를_계산한다() {
        var lock = policy.evaluate(group(24L, CONFIRMED_AT, CONFIRMED_AT.plusMinutes(5)),
                CONFIRMED_AT.plusMinutes(40));

        assertThat(lock.active()).isTrue();
        assertThat(lock.reason()).isEqualTo("MATCH_VALIDITY");
        assertThat(lock.groupId()).isEqualTo(24L);
        assertThat(lock.startsAt()).isEqualTo(CONFIRMED_AT);
        assertThat(lock.expiresAt()).isEqualTo(CONFIRMED_AT.plusHours(1));
        assertThat(lock.remainingSeconds()).isEqualTo(1_200);
    }

    @Test
    void 정확히_confirmedAt_1시간이면_inactive다() {
        var lock = policy.evaluate(group(24L, CONFIRMED_AT, CONFIRMED_AT.plusMinutes(30)),
                CONFIRMED_AT.plusHours(1));

        assertThat(lock.active()).isFalse();
        assertThat(lock.remainingSeconds()).isZero();
    }

    @Test
    void completedAt이_빠르거나_늦어도_confirmedAt을_기준으로_계산한다() {
        var early = policy.evaluate(group(24L, CONFIRMED_AT, CONFIRMED_AT.plusMinutes(1)),
                CONFIRMED_AT.plusMinutes(30));
        var late = policy.evaluate(group(25L, CONFIRMED_AT, CONFIRMED_AT.plusHours(2)),
                CONFIRMED_AT.plusMinutes(30));

        assertThat(early.expiresAt()).isEqualTo(CONFIRMED_AT.plusHours(1));
        assertThat(late.expiresAt()).isEqualTo(CONFIRMED_AT.plusHours(1));
    }

    private MatchGroup group(long id, OffsetDateTime confirmedAt, OffsetDateTime completedAt) {
        MatchGroup group = mock(MatchGroup.class);
        when(group.getId()).thenReturn(id);
        when(group.getConfirmedAt()).thenReturn(confirmedAt);
        when(group.getCompletedAt()).thenReturn(completedAt);
        return group;
    }
}
