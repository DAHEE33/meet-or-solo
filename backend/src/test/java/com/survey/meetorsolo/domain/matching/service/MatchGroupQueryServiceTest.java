package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.dto.MatchGroupResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository.ActiveGroupMemberProjection;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository.ActiveGroupWithFestivalProjection;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import com.survey.meetorsolo.domain.matching.config.MatchingArrivalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchGroupQueryServiceTest {

    private final MatchGroupRepository groups = mock(MatchGroupRepository.class);
    private final MatchGroupMemberRepository groupMembers = mock(MatchGroupMemberRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private MatchGroupQueryService service;

    @BeforeEach
    void setUp() {
        service = new MatchGroupQueryService(groups, groupMembers, members,
                new MatchingArrivalProperties(150, false));
        when(members.existsById(1L)).thenReturn(true);
    }

    @Test
    void active_group이_없으면_null을_반환한다() {
        when(groups.findActiveByMemberId(1L)).thenReturn(List.of());

        assertThat(service.currentGroup(1L)).isNull();
    }

    @Test
    void group과_확정_참여자를_결정적_조회_순서대로_매핑한다() {
        OffsetDateTime confirmedAt = OffsetDateTime.parse("2026-07-27T12:30:00+09:00");
        ActiveGroupWithFestivalProjection group = group(10L, 20L, "CONFIRMED", 2, confirmedAt);
        ActiveGroupMemberProjection first = participant(100L, 1L, "member-a", null, "JOINED");
        ActiveGroupMemberProjection second =
                participant(101L, 2L, "member-b", " https://example.com/b.png ", "ARRIVED");
        when(groups.findActiveByMemberId(1L)).thenReturn(List.of(group));
        when(groupMembers.findActiveMembersWithProfileByGroupId(10L))
                .thenReturn(List.of(first, second));

        var response = service.currentGroup(1L);

        assertThat(response.groupId()).isEqualTo(10L);
        assertThat(response.festivalId()).isEqualTo(20L);
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.confirmedAt()).isEqualTo(confirmedAt);
        assertThat(response.arrivalDeadlineAt()).isEqualTo(confirmedAt.plusMinutes(30));
        assertThat(response.confirmedMemberCount()).isEqualTo(response.members().size()).isEqualTo(2);
        assertThat(response.festival().festivalId()).isEqualTo(20L);
        assertThat(response.festival().title()).isEqualTo("테스트 축제");
        assertThat(response.festival().address()).isEqualTo("강원특별자치도 춘천시");
        assertThat(response.festival().eventStartDate()).isEqualTo(LocalDate.parse("2026-07-27"));
        assertThat(response.festival().eventEndDate()).isEqualTo(LocalDate.parse("2026-07-29"));
        assertThat(response.members()).extracting(member -> member.memberId())
                .containsExactly(1L, 2L);
        assertThat(response.members()).extracting(member -> member.nickname())
                .containsExactly("member-a", "member-b");
        assertThat(response.members().get(0).profileImageUrl()).isNull();
        assertThat(response.members().get(1).profileImageUrl())
                .isEqualTo("https://example.com/b.png");
        assertThat(response.members()).extracting(member -> member.status())
                .containsExactly("JOINED", "ARRIVED");
        assertThat(response.members()).extracting(member -> member.arrivalMinutes())
                .containsExactly((Integer) null, (Integer) null);
        verify(groups).findActiveByMemberId(1L);
        verify(groupMembers).findActiveMembersWithProfileByGroupId(10L);
    }

    @Test
    void IN_PROGRESS_group도_active_group으로_반환한다() {
        ActiveGroupWithFestivalProjection group = group(
                10L,
                20L,
                "IN_PROGRESS",
                2,
                OffsetDateTime.parse("2026-07-27T12:30:00+09:00")
        );
        ActiveGroupMemberProjection first = participant(100L, 1L, "member-a", null, "JOINED");
        ActiveGroupMemberProjection second = participant(101L, 2L, "member-b", null, "ARRIVAL_TIME_SELECTED");
        when(groups.findActiveByMemberId(1L)).thenReturn(List.of(group));
        when(groupMembers.findActiveMembersWithProfileByGroupId(10L))
                .thenReturn(List.of(first, second));

        var response = service.currentGroup(1L);
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.members().get(1).arrivalMinutes()).isEqualTo(10);
        assertThat(response.members().get(1).arrivalTimeSelectedAt())
                .isEqualTo(OffsetDateTime.parse("2026-07-27T12:35:00+09:00"));
    }

    @Test
    void current_group은_과거_0_30과_신규_25분을_그대로_반환한다() {
        ActiveGroupWithFestivalProjection group = group(
                10L, 20L, "CONFIRMED", 3,
                OffsetDateTime.parse("2026-07-27T12:30:00+09:00")
        );
        ActiveGroupMemberProjection zero =
                participant(100L, 1L, "member-a", null, "ARRIVAL_TIME_SELECTED");
        ActiveGroupMemberProjection thirty =
                participant(101L, 2L, "member-b", null, "ARRIVAL_TIME_SELECTED");
        ActiveGroupMemberProjection twentyFive =
                participant(102L, 3L, "member-c", null, "ARRIVAL_TIME_SELECTED");
        when(zero.getArrivalMinutes()).thenReturn(0);
        when(thirty.getArrivalMinutes()).thenReturn(30);
        when(twentyFive.getArrivalMinutes()).thenReturn(25);
        when(groups.findActiveByMemberId(1L)).thenReturn(List.of(group));
        when(groupMembers.findActiveMembersWithProfileByGroupId(10L))
                .thenReturn(List.of(zero, thirty, twentyFive));

        assertThat(service.currentGroup(1L).members())
                .extracting(member -> member.arrivalMinutes())
                .containsExactly(0, 30, 25);
    }

    @Test
    void active_group이_여러_개면_정합성_오류로_처리한다() {
        OffsetDateTime confirmedAt = OffsetDateTime.parse("2026-07-27T12:30:00+09:00");
        ActiveGroupWithFestivalProjection first = group(10L, 20L, "CONFIRMED", 2, confirmedAt);
        ActiveGroupWithFestivalProjection second = group(11L, 21L, "IN_PROGRESS", 2, confirmedAt);
        when(groups.findActiveByMemberId(1L)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.currentGroup(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode().getCode())
                .isEqualTo("MATCHING_CONFLICT");
    }

    /** 확정 인원보다 참여자가 많은 것은 여전히 있을 수 없는 상태다. */
    @Test
    void 확정_인원보다_참여자가_많으면_정합성_오류로_처리한다() {
        ActiveGroupWithFestivalProjection group = group(
                10L,
                20L,
                "CONFIRMED",
                1,
                OffsetDateTime.parse("2026-07-27T12:30:00+09:00")
        );
        // participant()가 내부에서 mock을 세우므로 thenReturn 안에서 만들면 중첩 stubbing이 된다.
        ActiveGroupMemberProjection first = participant(100L, 1L, "member-a", null, "JOINED");
        ActiveGroupMemberProjection second = participant(101L, 2L, "member-b", null, "JOINED");
        when(groups.findActiveByMemberId(1L)).thenReturn(List.of(group));
        when(groupMembers.findActiveMembersWithProfileByGroupId(10L))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.currentGroup(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 로그인_회원이_active_참여자에_없으면_정합성_오류로_처리한다() {
        ActiveGroupWithFestivalProjection group = group(
                10L,
                20L,
                "CONFIRMED",
                2,
                OffsetDateTime.parse("2026-07-27T12:30:00+09:00")
        );
        ActiveGroupMemberProjection first =
                participant(100L, 2L, "member-b", null, "JOINED");
        ActiveGroupMemberProjection second =
                participant(101L, 3L, "member-c", null, "JOINED");
        when(groups.findActiveByMemberId(1L)).thenReturn(List.of(group));
        when(groupMembers.findActiveMembersWithProfileByGroupId(10L))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.currentGroup(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode().getCode())
                .isEqualTo("MATCHING_CONFLICT");
    }

    @Test
    void 최초_세명에서_현재_두명이면_정상_snapshot과_두_count를_반환한다() {
        ActiveGroupWithFestivalProjection group = group(
                10L, 20L, "CONFIRMED", 3,
                OffsetDateTime.parse("2026-07-27T12:30:00+09:00"));
        ActiveGroupMemberProjection first =
                participant(100L, 1L, "member-a", null, "JOINED");
        ActiveGroupMemberProjection second =
                participant(101L, 2L, "member-b", null, "ARRIVED");
        when(groups.findActiveByMemberId(1L)).thenReturn(List.of(group));
        when(groupMembers.findActiveMembersWithProfileByGroupId(10L))
                .thenReturn(List.of(first, second));

        MatchGroupResponse response = service.currentGroup(1L);

        assertThat(response.confirmedMemberCount()).isEqualTo(3);
        assertThat(response.currentMemberCount()).isEqualTo(2);
        assertThat(response.members()).hasSize(2);
    }

    /**
     * 활성 참여자가 혼자 남는 것은 이제 정상이다({@code docs/19} 4.11.3).
     *
     * <p>만남이 성립한 방은 상대가 먼저 나가도 만남 시간이 끝날 때까지 유지된다. 예전에는 이
     * 상태를 정합성 오류로 보고 거절해서, 남은 사람이 상태방을 열지도 못했다.
     */
    @Test
    void 상대가_먼저_나가_혼자_남아도_정상_반환한다() {
        ActiveGroupWithFestivalProjection group = group(
                10L,
                20L,
                "IN_PROGRESS",
                2,
                OffsetDateTime.parse("2026-07-27T12:30:00+09:00")
        );
        ActiveGroupMemberProjection participant =
                participant(100L, 1L, "member-a", null, "ARRIVED");
        when(groups.findActiveByMemberId(1L)).thenReturn(List.of(group));
        when(groupMembers.findActiveMembersWithProfileByGroupId(10L))
                .thenReturn(List.of(participant));

        MatchGroupResponse response = service.currentGroup(1L);

        assertThat(response.currentMemberCount()).isEqualTo(1);
        assertThat(response.confirmedMemberCount()).isEqualTo(2);
    }

    private ActiveGroupWithFestivalProjection group(
            long id,
            long festivalId,
            String status,
            int confirmedMemberCount,
            OffsetDateTime confirmedAt
    ) {
        ActiveGroupWithFestivalProjection group = mock(ActiveGroupWithFestivalProjection.class);
        when(group.getGroupId()).thenReturn(id);
        when(group.getFestivalId()).thenReturn(festivalId);
        when(group.getStatus()).thenReturn(status);
        when(group.getConfirmedMemberCount()).thenReturn(confirmedMemberCount);
        when(group.getConfirmedAt()).thenReturn(confirmedAt.toInstant());
        when(group.getFestivalTitle()).thenReturn("테스트 축제");
        when(group.getFestivalAddress()).thenReturn("강원특별자치도 춘천시");
        when(group.getFestivalEventStartDate()).thenReturn(LocalDate.parse("2026-07-27"));
        when(group.getFestivalEventEndDate()).thenReturn(LocalDate.parse("2026-07-29"));
        return group;
    }

    private ActiveGroupMemberProjection participant(
            long groupMemberId,
            long memberId,
            String nickname,
            String profileImageUrl,
            String status
    ) {
        ActiveGroupMemberProjection participant = mock(ActiveGroupMemberProjection.class);
        when(participant.getGroupMemberId()).thenReturn(groupMemberId);
        when(participant.getMemberId()).thenReturn(memberId);
        when(participant.getNickname()).thenReturn(nickname);
        when(participant.getProfileImageUrl()).thenReturn(profileImageUrl);
        when(participant.getStatus()).thenReturn(status);
        when(participant.getArrivalMinutes()).thenReturn(null);
        when(participant.getArrivalTimeSelectedAt()).thenReturn(null);
        if ("ARRIVAL_TIME_SELECTED".equals(status)) {
            when(participant.getArrivalMinutes()).thenReturn(10);
            when(participant.getArrivalTimeSelectedAt())
                    .thenReturn(OffsetDateTime.parse("2026-07-27T12:35:00+09:00").toInstant());
        }
        return participant;
    }
}
