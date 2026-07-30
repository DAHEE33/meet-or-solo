package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.matching.dto.MatchGroupResponse;
import com.survey.meetorsolo.domain.matching.entity.MatchEvent;
import com.survey.meetorsolo.domain.matching.entity.MatchGroup;
import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import com.survey.meetorsolo.domain.matching.event.MatchingStateChangedEvent;
import com.survey.meetorsolo.domain.matching.repository.MatchEventRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class MatchArrivalTimeServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T03:35:00Z");
    private final MatchGroupRepository groups = mock(MatchGroupRepository.class);
    private final MatchGroupMemberRepository groupMembers =
            mock(MatchGroupMemberRepository.class);
    private final MatchEventRepository events = mock(MatchEventRepository.class);
    private final MatchGroupQueryService groupQueries = mock(MatchGroupQueryService.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final MatchGroupResponse snapshot = mock(MatchGroupResponse.class);
    private MatchArrivalTimeService service;

    @BeforeEach
    void setUp() {
        service = new MatchArrivalTimeService(
                Clock.fixed(
                        NOW,
                        ZoneId.of("Asia/Seoul")
                ),
                groups,
                groupMembers,
                events,
                groupQueries,
                publisher
        );
        when(groupQueries.currentGroup(1L)).thenReturn(snapshot);
    }

    @Test
    void group_then_member_잠금_후_JOINED를_변경하고_event와_알림을_발행한다() {
        MatchGroup group = activeGroup();
        MatchGroupMember member = member("JOINED", null);
        when(groups.findActiveByMemberIdForUpdate(1L)).thenReturn(List.of(group));
        when(groupMembers.findByGroupIdAndMemberIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(member));
        when(groupMembers.findActiveMemberIdsByGroupId(10L))
                .thenReturn(List.of(1L, 2L));

        assertThat(service.select(1L, 10)).isSameAs(snapshot);

        verify(groups).findActiveByMemberIdForUpdate(1L);
        verify(groupMembers).findByGroupIdAndMemberIdForUpdate(10L, 1L);
        verify(member).selectArrivalTime(eq(10), any());
        verify(groupMembers).flush();
        verify(events).saveAndFlush(any(MatchEvent.class));
        verify(publisher).publishEvent(any(MatchingStateChangedEvent.class));
    }

    @Test
    void 같은_값_반복은_event와_WebSocket_알림_없이_멱등_성공한다() {
        MatchGroup group = activeGroup();
        MatchGroupMember member = member("ARRIVAL_TIME_SELECTED", 10);
        when(groups.findActiveByMemberIdForUpdate(1L)).thenReturn(List.of(group));
        when(groupMembers.findByGroupIdAndMemberIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(member));

        assertThat(service.select(1L, 10)).isSameAs(snapshot);

        verify(member, never()).selectArrivalTime(any(Integer.class), any());
        verify(events, never()).saveAndFlush(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void 예상_도착_25분이_deadline과_같으면_허용한다() {
        MatchGroup group = activeGroup();
        when(group.getConfirmedAt()).thenReturn(
                OffsetDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul")).minusMinutes(5)
        );
        MatchGroupMember member = member("JOINED", null);
        when(groups.findActiveByMemberIdForUpdate(1L)).thenReturn(List.of(group));
        when(groupMembers.findByGroupIdAndMemberIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(member));

        assertThat(service.select(1L, 25)).isSameAs(snapshot);

        verify(member).selectArrivalTime(
                25,
                OffsetDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul"))
        );
    }

    @Test
    void 남은_시간보다_긴_선택은_deadline_오류로_거절한다() {
        MatchGroup group = activeGroup();
        when(group.getConfirmedAt()).thenReturn(
                OffsetDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul")).minusMinutes(20)
        );
        MatchGroupMember member = member("JOINED", null);
        when(groups.findActiveByMemberIdForUpdate(1L)).thenReturn(List.of(group));
        when(groupMembers.findByGroupIdAndMemberIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.select(1L, 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MATCHING_ARRIVAL_DEADLINE_EXCEEDED));

        verify(member, never()).selectArrivalTime(any(Integer.class), any());
        verify(events, never()).saveAndFlush(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void deadline_시각부터_같은_값_멱등_요청도_거절한다() {
        MatchGroup group = activeGroup();
        when(group.getConfirmedAt()).thenReturn(
                OffsetDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul")).minusMinutes(30)
        );
        MatchGroupMember member = member("ARRIVAL_TIME_SELECTED", 10);
        when(groups.findActiveByMemberIdForUpdate(1L)).thenReturn(List.of(group));
        when(groupMembers.findByGroupIdAndMemberIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.select(1L, 10))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MATCHING_ARRIVAL_DEADLINE_EXCEEDED));

        verify(member, never()).selectArrivalTime(any(Integer.class), any());
        verify(events, never()).saveAndFlush(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void deadline_전_같은_값은_현재시각으로_예정시각을_재계산하지_않는다() {
        MatchGroup group = activeGroup();
        when(group.getConfirmedAt()).thenReturn(
                OffsetDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul")).minusMinutes(20)
        );
        MatchGroupMember member = member("ARRIVAL_TIME_SELECTED", 25);
        when(groups.findActiveByMemberIdForUpdate(1L)).thenReturn(List.of(group));
        when(groupMembers.findByGroupIdAndMemberIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(member));

        assertThat(service.select(1L, 25)).isSameAs(snapshot);

        verify(member, never()).selectArrivalTime(any(Integer.class), any());
        verify(events, never()).saveAndFlush(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void ARRIVED_member는_변경을_거절한다() {
        MatchGroup group = activeGroup();
        MatchGroupMember member = member("ARRIVED", 10);
        when(groups.findActiveByMemberIdForUpdate(1L)).thenReturn(List.of(group));
        when(groupMembers.findByGroupIdAndMemberIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.select(1L, 5))
                .isInstanceOf(BusinessException.class);
        verify(events, never()).saveAndFlush(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void active_group이나_로그인_member가_없으면_동일한_충돌로_숨긴다() {
        when(groups.findActiveByMemberIdForUpdate(1L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.select(1L, 5))
                .isInstanceOf(BusinessException.class);

        MatchGroup group = activeGroup();
        when(groups.findActiveByMemberIdForUpdate(1L)).thenReturn(List.of(group));
        when(groupMembers.findByGroupIdAndMemberIdForUpdate(10L, 1L))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.select(1L, 5))
                .isInstanceOf(BusinessException.class);
    }

    private MatchGroup activeGroup() {
        MatchGroup group = mock(MatchGroup.class);
        when(group.getId()).thenReturn(10L);
        when(group.getAttemptId()).thenReturn(20L);
        when(group.getStatus()).thenReturn("CONFIRMED");
        when(group.getConfirmedAt()).thenReturn(
                OffsetDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul"))
        );
        return group;
    }

    private MatchGroupMember member(String status, Integer minutes) {
        MatchGroupMember member = mock(MatchGroupMember.class);
        when(member.getStatus()).thenReturn(status);
        when(member.getArrivalMinutes()).thenReturn(minutes);
        return member;
    }
}
