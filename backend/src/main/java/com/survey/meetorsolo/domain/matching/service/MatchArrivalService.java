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
        MatchGroup group = requireSingleActiveGroup(memberId);
        MatchGroupMember member = groupMembers
                .findByGroupIdAndMemberIdForUpdate(group.getId(), memberId)
                .orElseThrow(this::conflict);
        validateLockedState(group, member);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!now.isBefore(MatchArrivalDeadlinePolicy.deadlineAt(group.getConfirmedAt()))) {
            throw new BusinessException(ErrorCode.MATCHING_ARRIVAL_DEADLINE_EXCEEDED);
        }
        if ("ARRIVED".equals(member.getStatus())) {
            return requireSnapshot(memberId);
        }

        member.arrive(now);
        group.start(now);
        groupMembers.flush();
        groups.flush();
        events.saveAndFlush(MatchEvent.memberArrived(
                group.getId(), group.getAttemptId(), memberId, now
        ));
        List<Long> activeMemberIds = groupMembers.findActiveMemberIdsByGroupId(group.getId());
        eventPublisher.publishEvent(new MatchingStateChangedEvent(
                activeMemberIds, "MEMBER_ARRIVED", now
        ));
        return requireSnapshot(memberId);
    }

    private MatchGroup requireSingleActiveGroup(long memberId) {
        List<MatchGroup> activeGroups = groups.findActiveByMemberIdForUpdate(memberId);
        if (activeGroups.size() != 1) throw conflict();
        return activeGroups.get(0);
    }

    private void validateLockedState(MatchGroup group, MatchGroupMember member) {
        if ((!"CONFIRMED".equals(group.getStatus()) && !"IN_PROGRESS".equals(group.getStatus()))
                || (!"JOINED".equals(member.getStatus())
                && !"ARRIVAL_TIME_SELECTED".equals(member.getStatus())
                && !"ARRIVED".equals(member.getStatus()))) {
            throw conflict();
        }
    }

    private MatchGroupResponse requireSnapshot(long memberId) {
        MatchGroupResponse snapshot = groupQueries.currentGroup(memberId);
        if (snapshot == null) throw conflict();
        return snapshot;
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.MATCHING_CONFLICT);
    }
}
