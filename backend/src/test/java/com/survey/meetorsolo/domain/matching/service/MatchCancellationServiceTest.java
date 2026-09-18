package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.matching.dto.MatchCancellationReason;
import com.survey.meetorsolo.domain.matching.entity.MatchGroup;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPoint;
import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import com.survey.meetorsolo.domain.matching.repository.MatchEventRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class MatchCancellationServiceTest {
    private static final OffsetDateTime CONFIRMED =
            OffsetDateTime.parse("2026-07-31T12:00:00+09:00");
    private final MatchGroupRepository groups = mock(MatchGroupRepository.class);
    private final MatchGroupMemberRepository groupMembers =
            mock(MatchGroupMemberRepository.class);
    private final MatchEventRepository events = mock(MatchEventRepository.class);
    private final MatchRoomPenaltyService penalties = mock(MatchRoomPenaltyService.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    @Test
    void 확정_정확히_3분_취소는_무패널티이고_동의한_두명_group을_유지한다() {
        MatchGroup group = group();
        MatchGroupMember actor = member(101, 1, true);
        MatchGroupMember second = member(102, 2, true);
        MatchGroupMember third = member(103, 3, true);
        when(groups.findActiveByMemberIdForUpdate(1)).thenReturn(List.of(group));
        when(groupMembers.findAllByGroupIdForUpdate(10))
                .thenReturn(List.of(actor, second, third));

        MatchCancellationService service = service(CONFIRMED.plusMinutes(3));
        var result = service.cancel(1, MatchCancellationReason.SCHEDULE_CHANGED);

        assertThat(result.groupContinues()).isTrue();
        assertThat(result.currentMemberCount()).isEqualTo(2);
        assertThat(actor.getStatus()).isEqualTo("CANCELLED");
        verify(penalties, never()).applyCancellation(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 확정_3분_이후_취소는_penalty를_적용한다() {
        MatchGroup group = group();
        MatchGroupMember actor = member(101, 1, true);
        MatchGroupMember second = member(102, 2, true);
        MatchGroupMember third = member(103, 3, true);
        when(groups.findActiveByMemberIdForUpdate(1)).thenReturn(List.of(group));
        when(groupMembers.findAllByGroupIdForUpdate(10))
                .thenReturn(List.of(actor, second, third));

        OffsetDateTime now = CONFIRMED.plusMinutes(3).plusNanos(1);
        service(now).cancel(1, MatchCancellationReason.OTHER);

        verify(penalties).applyCancellation(10, 20, 1, now);
    }

    private MatchCancellationService service(OffsetDateTime now) {
        return new MatchCancellationService(
                Clock.fixed(now.toInstant(), ZoneId.of("Asia/Seoul")),
                groups, groupMembers, events, penalties,
                new MatchGroupContinuationPolicy(), publisher);
    }

    private MatchGroup group() {
        MatchGroup group = MatchGroup.confirmed(20, 30, 3, meetingPoint(), CONFIRMED);
        ReflectionTestUtils.setField(group, "id", 10L);
        return group;
    }

    private FestivalMeetingPoint meetingPoint() {
        return FestivalMeetingPoint.inactive(30, "test-place", "테스트 장소", "강원 테스트로 1",
                new java.math.BigDecimal("128.1"), new java.math.BigDecimal("37.1"), 1);
    }

    private MatchGroupMember member(long id, long memberId, boolean allowMinimumTwo) {
        MatchGroupMember member = MatchGroupMember.joined(
                10, memberId, allowMinimumTwo, CONFIRMED);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
