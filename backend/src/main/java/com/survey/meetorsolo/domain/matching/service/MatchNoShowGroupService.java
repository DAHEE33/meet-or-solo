package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchEvent;
import com.survey.meetorsolo.domain.matching.entity.MatchGroup;
import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import com.survey.meetorsolo.domain.matching.event.MatchingStateChangedEvent;
import com.survey.meetorsolo.domain.matching.repository.MatchEventRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchNoShowGroupService {
    private final MatchGroupRepository groups;
    private final MatchGroupMemberRepository groupMembers;
    private final MatchEventRepository events;
    private final MatchRoomPenaltyService penalties;
    private final MatchGroupContinuationPolicy continuationPolicy;
    private final ApplicationEventPublisher publisher;

    public MatchNoShowGroupService(MatchGroupRepository groups,
            MatchGroupMemberRepository groupMembers, MatchEventRepository events,
            MatchRoomPenaltyService penalties, MatchGroupContinuationPolicy continuationPolicy,
            ApplicationEventPublisher publisher) {
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.events = events;
        this.penalties = penalties;
        this.continuationPolicy = continuationPolicy;
        this.publisher = publisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean process(long groupId, OffsetDateTime now) {
        MatchGroup group = groups.tryLockActiveById(groupId).orElse(null);
        if (group == null
                || MatchArrivalDeadlinePolicy.deadlineAt(group.getConfirmedAt()).isAfter(now)) {
            return false;
        }
        List<MatchGroupMember> members = groupMembers.findAllByGroupIdForUpdate(groupId);
        List<Long> notified = members.stream().filter(this::isActive)
                .map(MatchGroupMember::getMemberId).toList();
        List<MatchGroupMember> noShows = members.stream()
                .filter(member -> "JOINED".equals(member.getStatus())
                        || "ARRIVAL_TIME_SELECTED".equals(member.getStatus()))
                .sorted(java.util.Comparator.comparing(MatchGroupMember::getId))
                .toList();
        if (noShows.isEmpty()) return false;
        for (MatchGroupMember member : noShows) {
            member.noShow(now);
            events.save(MatchEvent.memberNoShow(
                    groupId, group.getAttemptId(), member.getMemberId(), now));
            penalties.applyNoShow(
                    groupId, group.getAttemptId(), member.getMemberId(), now);
        }
        List<MatchGroupMember> active = members.stream().filter(this::isActive).toList();
        String reason = continuationPolicy.cancellationReason(active);
        String notificationReason = "MEMBER_NO_SHOW";
        if (reason != null) {
            group.cancel(reason, now);
            active.forEach(member -> member.leave(now));
            events.save(MatchEvent.matchCancelled(groupId, group.getAttemptId(), reason, now));
            notificationReason = "MATCH_CANCELLED";
        }
        groupMembers.flush();
        groups.flush();
        events.flush();
        publisher.publishEvent(new MatchingStateChangedEvent(notified, notificationReason, now));
        return true;
    }

    private boolean isActive(MatchGroupMember member) {
        return "JOINED".equals(member.getStatus())
                || "ARRIVAL_TIME_SELECTED".equals(member.getStatus())
                || "ARRIVED".equals(member.getStatus());
    }
}
