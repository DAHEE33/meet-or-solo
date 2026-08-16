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
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchArrivalTimeService {

    private static final Set<Integer> ALLOWED_MINUTES = Set.of(5, 10, 20, 25);
    private final Clock clock;
    private final MatchGroupRepository groups;
    private final MatchGroupMemberRepository groupMembers;
    private final MatchEventRepository events;
    private final MatchGroupQueryService groupQueries;
    private final ApplicationEventPublisher eventPublisher;

    public MatchArrivalTimeService(
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
    public MatchGroupResponse select(long memberId, int arrivalMinutes) {
        if (!ALLOWED_MINUTES.contains(arrivalMinutes)) {
            throw new BusinessException(ErrorCode.MATCHING_INVALID_REQUEST);
        }
        MatchGroup group = requireSingleActiveGroup(memberId);
        MatchGroupMember member = groupMembers
                .findByGroupIdAndMemberIdForUpdate(group.getId(), memberId)
                .orElseThrow(this::conflict);
        validateLockedState(group, member);

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime deadlineAt = MatchArrivalDeadlinePolicy.deadlineAt(group.getConfirmedAt());
        validateBeforeDeadline(now, deadlineAt);

        if ("ARRIVAL_TIME_SELECTED".equals(member.getStatus())
                && Integer.valueOf(arrivalMinutes).equals(member.getArrivalMinutes())) {
            return requireSnapshot(memberId);
        }

        validateEstimatedArrival(now, arrivalMinutes, deadlineAt);
        member.selectArrivalTime(arrivalMinutes, now);
        groupMembers.flush();
        events.saveAndFlush(MatchEvent.arrivalTimeSelected(
                group.getId(),
                group.getAttemptId(),
                memberId,
                arrivalMinutes,
                now
        ));
        List<Long> activeMemberIds = groupMembers.findActiveMemberIdsByGroupId(group.getId());
        eventPublisher.publishEvent(new MatchingStateChangedEvent(
                activeMemberIds,
                "ARRIVAL_TIME_SELECTED",
                now
        ));
        return requireSnapshot(memberId);
    }

    private MatchGroup requireSingleActiveGroup(long memberId) {
        List<MatchGroup> activeGroups = groups.findActiveByMemberIdForUpdate(memberId);
        if (activeGroups.size() != 1) {
            throw conflict();
        }
        return activeGroups.get(0);
    }

    private void validateLockedState(MatchGroup group, MatchGroupMember member) {
        if (!"CONFIRMED".equals(group.getStatus()) && !"IN_PROGRESS".equals(group.getStatus())) {
            throw conflict();
        }
        if ("ARRIVED".equals(member.getStatus())
                || (!"JOINED".equals(member.getStatus())
                && !"ARRIVAL_TIME_SELECTED".equals(member.getStatus()))) {
            throw conflict();
        }
    }

    private void validateBeforeDeadline(OffsetDateTime now, OffsetDateTime deadlineAt) {
        if (!now.isBefore(deadlineAt)) {
            throw deadlineExceeded();
        }
    }

    private void validateEstimatedArrival(
            OffsetDateTime now,
            int arrivalMinutes,
            OffsetDateTime deadlineAt
    ) {
        if (now.plusMinutes(arrivalMinutes).isAfter(deadlineAt)) {
            throw deadlineExceeded();
        }
    }

    private BusinessException deadlineExceeded() {
        return new BusinessException(ErrorCode.MATCHING_ARRIVAL_DEADLINE_EXCEEDED);
    }

    private MatchGroupResponse requireSnapshot(long memberId) {
        MatchGroupResponse snapshot = groupQueries.currentGroup(memberId);
        if (snapshot == null) {
            throw conflict();
        }
        return snapshot;
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.MATCHING_CONFLICT);
    }
}
