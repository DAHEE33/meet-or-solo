package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.matching.entity.MatchGroup;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository.ActiveGroupMemberProjection;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchGroupQueryServiceTest {

    private final MatchGroupRepository groups = mock(MatchGroupRepository.class);
    private final MatchGroupMemberRepository groupMembers = mock(MatchGroupMemberRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private MatchGroupQueryService service;

    @BeforeEach
    void setUp() {
        service = new MatchGroupQueryService(groups, groupMembers, members);
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
        MatchGroup group = group(10L, 20L, "CONFIRMED", 2, confirmedAt);
        ActiveGroupMemberProjection first = participant(100L, 1L, "member-a", null);
        ActiveGroupMemberProjection second =
                participant(101L, 2L, "member-b", " https://example.com/b.png ");
        when(groups.findActiveByMemberId(1L)).thenReturn(List.of(group));
        when(groupMembers.findActiveMembersWithProfileByGroupId(10L))
                .thenReturn(List.of(first, second));

        var response = service.currentGroup(1L);

        assertThat(response.groupId()).isEqualTo(10L);
        assertThat(response.festivalId()).isEqualTo(20L);
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.confirmedAt()).isEqualTo(confirmedAt);
        assertThat(response.confirmedMemberCount()).isEqualTo(response.members().size()).isEqualTo(2);
        assertThat(response.members()).extracting(member -> member.memberId())
                .containsExactly(1L, 2L);
        assertThat(response.members()).extracting(member -> member.nickname())
                .containsExactly("member-a", "member-b");
        assertThat(response.members().get(0).profileImageUrl()).isNull();
        assertThat(response.members().get(1).profileImageUrl())
                .isEqualTo("https://example.com/b.png");
    }

    @Test
    void IN_PROGRESS_group도_active_group으로_반환한다() {
        MatchGroup group = group(
                10L,
                20L,
                "IN_PROGRESS",
                2,
                OffsetDateTime.parse("2026-07-27T12:30:00+09:00")
        );
        ActiveGroupMemberProjection first = participant(100L, 1L, "member-a", null);
        ActiveGroupMemberProjection second = participant(101L, 2L, "member-b", null);
        when(groups.findActiveByMemberId(1L)).thenReturn(List.of(group));
        when(groupMembers.findActiveMembersWithProfileByGroupId(10L))
                .thenReturn(List.of(first, second));

        assertThat(service.currentGroup(1L).status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void active_group이_여러_개면_정합성_오류로_처리한다() {
        OffsetDateTime confirmedAt = OffsetDateTime.parse("2026-07-27T12:30:00+09:00");
        MatchGroup first = group(10L, 20L, "CONFIRMED", 2, confirmedAt);
        MatchGroup second = group(11L, 21L, "IN_PROGRESS", 2, confirmedAt);
        when(groups.findActiveByMemberId(1L)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.currentGroup(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode().getCode())
                .isEqualTo("MATCHING_CONFLICT");
    }

    @Test
    void 저장된_확정_인원과_실제_참여자_수가_다르면_정합성_오류로_처리한다() {
        MatchGroup group = group(
                10L,
                20L,
                "CONFIRMED",
                2,
                OffsetDateTime.parse("2026-07-27T12:30:00+09:00")
        );
        ActiveGroupMemberProjection participant =
                participant(100L, 1L, "member-a", null);
        when(groups.findActiveByMemberId(1L)).thenReturn(List.of(group));
        when(groupMembers.findActiveMembersWithProfileByGroupId(10L))
                .thenReturn(List.of(participant));

        assertThatThrownBy(() -> service.currentGroup(1L))
                .isInstanceOf(BusinessException.class);
    }

    private MatchGroup group(
            long id,
            long festivalId,
            String status,
            int confirmedMemberCount,
            OffsetDateTime confirmedAt
    ) {
        MatchGroup group = mock(MatchGroup.class);
        when(group.getId()).thenReturn(id);
        when(group.getFestivalId()).thenReturn(festivalId);
        when(group.getStatus()).thenReturn(status);
        when(group.getConfirmedMemberCount()).thenReturn(confirmedMemberCount);
        when(group.getConfirmedAt()).thenReturn(confirmedAt);
        return group;
    }

    private ActiveGroupMemberProjection participant(
            long groupMemberId,
            long memberId,
            String nickname,
            String profileImageUrl
    ) {
        ActiveGroupMemberProjection participant = mock(ActiveGroupMemberProjection.class);
        when(participant.getGroupMemberId()).thenReturn(groupMemberId);
        when(participant.getMemberId()).thenReturn(memberId);
        when(participant.getNickname()).thenReturn(nickname);
        when(participant.getProfileImageUrl()).thenReturn(profileImageUrl);
        return participant;
    }
}
