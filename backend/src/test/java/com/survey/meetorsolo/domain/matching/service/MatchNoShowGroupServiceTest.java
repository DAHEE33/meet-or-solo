package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.matching.entity.MatchGroup;
import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import com.survey.meetorsolo.domain.matching.repository.MatchEventRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class MatchNoShowGroupServiceTest {
    @Test
    void deadline_정각에_미도착만_NO_SHOW로_전환하고_도착한_두명은_유지한다() {
        OffsetDateTime confirmed = OffsetDateTime.parse("2026-07-31T12:00:00+09:00");
        OffsetDateTime deadline = confirmed.plusMinutes(30);
        MatchGroup group = MatchGroup.confirmed(20, 30, 3, confirmed);
        ReflectionTestUtils.setField(group, "id", 10L);
        MatchGroupMember noShow = member(101, 1, true, confirmed);
        MatchGroupMember arrivedA = member(102, 2, true, confirmed);
        MatchGroupMember arrivedB = member(103, 3, true, confirmed);
        arrivedA.arrive(confirmed.plusMinutes(5));
        arrivedB.arrive(confirmed.plusMinutes(6));

        MatchGroupRepository groups = mock(MatchGroupRepository.class);
        MatchGroupMemberRepository members = mock(MatchGroupMemberRepository.class);
        MatchEventRepository events = mock(MatchEventRepository.class);
        MatchRoomPenaltyService penalties = mock(MatchRoomPenaltyService.class);
        when(groups.tryLockActiveById(10)).thenReturn(Optional.of(group));
        when(members.findAllByGroupIdForUpdate(10))
                .thenReturn(List.of(noShow, arrivedA, arrivedB));

        MatchNoShowGroupService service = new MatchNoShowGroupService(
                groups, members, events, penalties,
                new MatchGroupContinuationPolicy(), mock(ApplicationEventPublisher.class));

        assertThat(service.process(10, deadline)).isTrue();
        assertThat(noShow.getStatus()).isEqualTo("NO_SHOW");
        assertThat(arrivedA.getStatus()).isEqualTo("ARRIVED");
        assertThat(arrivedB.getStatus()).isEqualTo("ARRIVED");
        assertThat(group.getStatus()).isEqualTo("CONFIRMED");
        verify(penalties).applyNoShow(10, 20, 1, deadline);
    }

    private MatchGroupMember member(long id, long memberId, boolean allowMinimumTwo,
            OffsetDateTime now) {
        MatchGroupMember member = MatchGroupMember.joined(
                10, memberId, allowMinimumTwo, now);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
