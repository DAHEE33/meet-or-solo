package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import com.survey.meetorsolo.domain.matching.repository.MatchCooldownRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchPenaltyEventRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MatchPoolCancellationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant FIXED = Instant.parse("2026-08-20T05:00:00Z"); // KST 14:00
    private static final Clock CLOCK = Clock.fixed(FIXED, SEOUL);

    private final MatchPoolRepository pools = mock(MatchPoolRepository.class);
    private final MatchCooldownRepository cooldowns = mock(MatchCooldownRepository.class);
    private final MatchPenaltyEventRepository penaltyEvents = mock(MatchPenaltyEventRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);

    private MatchPoolCancellationService service() {
        return new MatchPoolCancellationService(CLOCK, pools, cooldowns, penaltyEvents, members);
    }

    private MatchPool waitingPool(long memberId) {
        OffsetDateTime now = OffsetDateTime.now(CLOCK);
        MatchPool pool = MatchPool.waiting(memberId, 1L, 100L, 3, true, List.of(), now, now.plusSeconds(60));
        ReflectionTestUtils.setField(pool, "id", 999L);
        return pool;
    }

    @Test
    void WAITING_풀을_취소하면_CANCELLED_상태가_된다() {
        MatchPool pool = waitingPool(1L);
        when(pools.findActiveCancellablePoolForUpdate(1L)).thenReturn(Optional.of(pool));
        when(cooldowns.existsByRelatedPoolId(anyLong())).thenReturn(false);
        when(cooldowns.countTodayByReason(eq(1L), eq("POOL_CANCEL"), any(), any())).thenReturn(0);

        MatchPoolCancellationResult result = service().cancel(1L);

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(pool.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void LOCKED_풀도_취소할_수_있다() {
        MatchPool pool = waitingPool(1L);
        pool.lock(OffsetDateTime.now(CLOCK), "token");
        when(pools.findActiveCancellablePoolForUpdate(1L)).thenReturn(Optional.of(pool));
        when(cooldowns.existsByRelatedPoolId(anyLong())).thenReturn(false);
        when(cooldowns.countTodayByReason(eq(1L), eq("POOL_CANCEL"), any(), any())).thenReturn(0);

        MatchPoolCancellationResult result = service().cancel(1L);

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(pool.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void PROPOSED_풀은_409_CONFLICT를_반환한다() {
        MatchPool pool = waitingPool(1L);
        pool.lock(OffsetDateTime.now(CLOCK), "token");
        pool.propose(OffsetDateTime.now(CLOCK));
        when(pools.findActiveCancellablePoolForUpdate(1L)).thenReturn(Optional.of(pool));

        assertThatThrownBy(() -> service().cancel(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("매칭 제안이 진행 중");
    }

    @Test
    void 활성_풀이_없으면_404를_반환한다() {
        when(pools.findActiveCancellablePoolForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().cancel(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("취소할 활성 매칭풀이 없습니다");
    }

    @Test
    void 당일_1회째_취소는_penalty_없이_cooldown만_생성한다() {
        MatchPool pool = waitingPool(1L);
        when(pools.findActiveCancellablePoolForUpdate(1L)).thenReturn(Optional.of(pool));
        when(cooldowns.existsByRelatedPoolId(anyLong())).thenReturn(false);
        when(cooldowns.countTodayByReason(eq(1L), eq("POOL_CANCEL"), any(), any())).thenReturn(0);

        service().cancel(1L);

        verify(cooldowns).save(any());
        verify(penaltyEvents, never()).save(any());
        verify(members, never()).increasePenaltyScore(anyLong(), anyInt());
    }

    @Test
    void 당일_3회째_취소는_penalty_1점과_cooldown을_생성한다() {
        MatchPool pool = waitingPool(1L);
        when(pools.findActiveCancellablePoolForUpdate(1L)).thenReturn(Optional.of(pool));
        when(cooldowns.existsByRelatedPoolId(anyLong())).thenReturn(false);
        when(cooldowns.countTodayByReason(eq(1L), eq("POOL_CANCEL"), any(), any())).thenReturn(2);
        when(penaltyEvents.existsByRelatedPoolId(anyLong())).thenReturn(false);
        when(members.increasePenaltyScore(1L, 1)).thenReturn(1);

        service().cancel(1L);

        verify(cooldowns).save(any());
        verify(penaltyEvents).save(any());
        verify(members).increasePenaltyScore(1L, 1);
    }

    @Test
    void 같은_pool에_이미_cooldown이_있으면_중복_생성하지_않는다() {
        MatchPool pool = waitingPool(1L);
        when(pools.findActiveCancellablePoolForUpdate(1L)).thenReturn(Optional.of(pool));
        when(cooldowns.existsByRelatedPoolId(anyLong())).thenReturn(true);

        service().cancel(1L);

        verify(cooldowns, never()).countTodayByReason(anyLong(), anyString(), any(), any());
        verify(cooldowns, never()).save(any());
    }
}
