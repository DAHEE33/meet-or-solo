package com.survey.meetorsolo.domain.matching.service;

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
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchArrivalService {

    private final Clock clock;
    private final MatchGroupRepository groups;
    private final MatchGroupMemberRepository groupMembers;
    private final MatchEventRepository events;
    private final MatchGroupQueryService groupQueries;
    private final ApplicationEventPublisher eventPublisher;

    public MatchArrivalService(
            Clock clock,
            MatchGroupRepository groups,
            MatchGroupMemberRepository groupMembers,
            MatchEventRepository events,
            MatchGroupQueryService groupQueries,
            ApplicationEventPublisher eventPublisher
    ) {
        this.clock = clock;
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.events = events;
        this.groupQueries = groupQueries;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public MatchGroupResponse arrive(long memberId) {
        MatchGroup group = findActiveOrLatestCompletedGroup(memberId);
        List<MatchGroupMember> members = groupMembers.findAllByGroupIdForUpdate(group.getId());
        MatchGroupMember member = members.stream()
                .filter(candidate -> candidate.getMemberId() == memberId)
                .findFirst()
                .orElseThrow(this::conflict);
        if ("COMPLETED".equals(group.getStatus())) {
            if (!"COMPLETED".equals(member.getStatus())) throw conflict();
            return groupQueries.snapshot(group.getId(), memberId);
        }
        validateLockedState(group, member);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!now.isBefore(MatchArrivalDeadlinePolicy.deadlineAt(group.getConfirmedAt()))) {
            throw new BusinessException(ErrorCode.MATCHING_ARRIVAL_DEADLINE_EXCEEDED);
        }
        if ("ARRIVED".equals(member.getStatus())) {
            return groupQueries.snapshot(group.getId(), memberId);
        }
        member.arrive(now);
        group.start(now);
        events.save(MatchEvent.memberArrived(
                group.getId(), group.getAttemptId(), memberId, now
        ));

        // 전원이 도착해도 여기서 완료로 닫지 않는다. 도착은 만남의 시작이고 완료 판정은
        // 확정 + MatchMeetingWindowPolicy.MEETING_WINDOW에서 MatchMeetingCloseGroupService가
        // 한다(docs/19 4.11.2). 예전에는 마지막 도착자가 버튼을 누르는 순간 상태방이 사라졌고,
        // 활성 구성원이 한 명만 남은 그룹에서는 단독 도착이 완료·보상으로 이어졌다.
        List<MatchGroupMember> activeMembers = members.stream().filter(this::isActive).toList();
        List<Long> activeMemberIds = activeMembers.stream()
                .map(MatchGroupMember::getMemberId)
                .toList();
        boolean allArrived = !activeMembers.isEmpty()
                && activeMembers.stream().allMatch(candidate -> "ARRIVED".equals(candidate.getStatus()));
        groupMembers.flush();
        groups.flush();
        events.flush();
        eventPublisher.publishEvent(new MatchingStateChangedEvent(
                activeMemberIds, allArrived ? "ALL_ARRIVED" : "MEMBER_ARRIVED", now
        ));
        return groupQueries.snapshot(group.getId(), memberId);
    }

    private MatchGroup findActiveOrLatestCompletedGroup(long memberId) {
        List<MatchGroup> activeGroups = groups.findActiveByMemberIdForUpdate(memberId);
        if (activeGroups.size() > 1) throw conflict();
        if (activeGroups.size() == 1) return activeGroups.get(0);
        return groups.findLatestCompletedByMemberIdForUpdate(memberId)
                .orElseThrow(this::conflict);
    }

    private void validateLockedState(MatchGroup group, MatchGroupMember member) {
        if ((!"CONFIRMED".equals(group.getStatus()) && !"IN_PROGRESS".equals(group.getStatus()))
                || (!"JOINED".equals(member.getStatus())
                && !"ARRIVAL_TIME_SELECTED".equals(member.getStatus())
                && !"ARRIVED".equals(member.getStatus()))) {
            throw conflict();
        }
    }

    private boolean isActive(MatchGroupMember member) {
        return "JOINED".equals(member.getStatus())
                || "ARRIVAL_TIME_SELECTED".equals(member.getStatus())
                || "ARRIVED".equals(member.getStatus());
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.MATCHING_CONFLICT);
    }
}
