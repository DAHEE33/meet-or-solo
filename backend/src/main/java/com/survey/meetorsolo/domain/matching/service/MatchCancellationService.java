package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.dto.MatchCancellationReason;
import com.survey.meetorsolo.domain.matching.dto.MatchCancellationResponse;
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
public class MatchCancellationService {
    private final Clock clock;
    private final MatchGroupRepository groups;
    private final MatchGroupMemberRepository groupMembers;
    private final MatchEventRepository events;
    private final MatchRoomPenaltyService penalties;
    private final MatchGroupContinuationPolicy continuationPolicy;
    private final ApplicationEventPublisher publisher;

    public MatchCancellationService(Clock clock, MatchGroupRepository groups,
            MatchGroupMemberRepository groupMembers, MatchEventRepository events,
            MatchRoomPenaltyService penalties, MatchGroupContinuationPolicy continuationPolicy,
            ApplicationEventPublisher publisher) {
        this.clock = clock;
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.events = events;
        this.penalties = penalties;
        this.continuationPolicy = continuationPolicy;
        this.publisher = publisher;
    }

    @Transactional
    public MatchCancellationResponse cancel(long memberId, MatchCancellationReason reason) {
        List<MatchGroup> activeGroups = groups.findActiveByMemberIdForUpdate(memberId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (activeGroups.isEmpty()) return previousCancellation(memberId, now);
        if (activeGroups.size() != 1) throw conflict();
        MatchGroup group = activeGroups.get(0);
        List<MatchGroupMember> members = groupMembers.findAllByGroupIdForUpdate(group.getId());
        MatchGroupMember actor = members.stream()
                .filter(value -> value.getMemberId() == memberId)
                .findFirst().orElseThrow(this::conflict);
        if (!"JOINED".equals(actor.getStatus())
                && !"ARRIVAL_TIME_SELECTED".equals(actor.getStatus())) throw conflict();

        if (!now.isBefore(MatchArrivalDeadlinePolicy.deadlineAt(group.getConfirmedAt()))) {
            throw new BusinessException(ErrorCode.MATCHING_ARRIVAL_DEADLINE_EXCEEDED);
        }
        List<Long> notified = activeMemberIds(members);
        actor.cancel(reason.name(), now);
        events.save(MatchEvent.memberCancelled(
                group.getId(), group.getAttemptId(), memberId, reason.name(), now));
        if (!now.isAfter(group.getConfirmedAt().plusMinutes(3))) {
            // 확정 후 정확히 3분까지는 무패널티다.
        } else {
            penalties.applyCancellation(group.getId(), group.getAttemptId(), memberId, now);
        }
        int currentCount = finishGroupIfNeeded(group, members, now);
        groupMembers.flush();
        groups.flush();
        events.flush();
        publisher.publishEvent(new MatchingStateChangedEvent(
                notified, group.getStatus().equals("CANCELLED")
                        ? "MATCH_CANCELLED" : "MEMBER_CANCELLED", now));
        return response(group, currentCount);
    }

    private MatchCancellationResponse previousCancellation(long memberId, OffsetDateTime now) {
        MatchGroupMember member = groupMembers.findLatestCancelledByMemberId(memberId, now)
                .orElseThrow(this::conflict);
        MatchGroup group = groups.findById(member.getGroupId()).orElseThrow(this::conflict);
        int count = groupMembers.findActiveMemberIdsByGroupId(group.getId()).size();
        return response(group, count);
    }

    private int finishGroupIfNeeded(MatchGroup group, List<MatchGroupMember> members,
            OffsetDateTime now) {
        List<MatchGroupMember> active = members.stream().filter(this::isActive).toList();
        String reason = continuationPolicy.cancellationReason(active);
        if (reason == null) return active.size();
        group.cancel(reason, now);
        active.forEach(member -> member.leave(now));
        events.save(MatchEvent.matchCancelled(group.getId(), group.getAttemptId(), reason, now));
        return 0;
    }

    private MatchCancellationResponse response(MatchGroup group, int currentCount) {
        return new MatchCancellationResponse(group.getId(), "CANCELLED", group.getStatus(),
                !"CANCELLED".equals(group.getStatus()), currentCount);
    }

    private List<Long> activeMemberIds(List<MatchGroupMember> members) {
        return members.stream().filter(this::isActive).map(MatchGroupMember::getMemberId).toList();
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
