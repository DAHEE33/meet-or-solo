package com.survey.meetorsolo.domain.matching.service;

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

/**
 * 도착한 사람이 만남에서 먼저 나간다({@code docs/19} 4.11.3).
 *
 * <p><b>페널티가 없다.</b> 이 기능은 벌을 정하는 자리가 아니라 <b>빠져나올 문</b>이다. 필요한
 * 상황이 둘이다.
 *
 * <ol>
 *   <li>혼자 도착해 기다리다 포기할 때. 예전에는 도착을 누르면 도착 마감(30분)까지 묶여서
 *       상대가 오지 않아도 빠져나올 수 없었다.</li>
 *   <li>자리가 불편하거나 무서울 때. 자리를 뜨는 것을 앱이 막아서는 안 되고, 거기에 페널티를
 *       붙이면 더 나쁘다.</li>
 * </ol>
 *
 * <p><b>머문 시간을 재지 않는다.</b> 확정 + {@link MatchMeetingWindowPolicy#MEETING_WINDOW}는
 * 방을 닫는 행정적 시각이지 그만큼 머물기로 한 약속이 아니다. 40분 놀다 헤어진 것과 1시간을 채운
 * 것을 가를 근거가 없으므로, 먼저 갔는지 끝까지 있었는지로 보상을 가르지 않는다. 판정은 오직
 * <b>만남이 성립했는가</b>(도착자 2명 이상)뿐이다.
 *
 * <p><b>만남이 성립한 뒤에만 쓸 수 있다.</b> 도착자가 나 혼자인데도 무패널티로 나갈 수 있으면,
 * 도착 버튼을 눌렀다 나가는 것으로 취소 페널티를 피하는 샛길이 된다. 그 상태에서 나가는 것은
 * 오고 있는 사람을 두고 떠나는 것이므로 참여 취소({@code MatchCancellationService})와 같은
 * 규칙을 받는다.
 *
 * <p>그래서 이 서비스가 하는 일은 셋이다 — 구성원을 이탈로 바꾸고, 타임라인에 남기고, 남은
 * 인원으로 방을 이어갈 수 있는지 판정한다. 보상과 재매칭 잠금은 건드리지 않는다. 그쪽은
 * {@code arrived_at}만 보므로 이탈해도 결과가 같다.
 */
@Service
public class MatchLeaveService {

    private final Clock clock;
    private final MatchGroupRepository groups;
    private final MatchGroupMemberRepository groupMembers;
    private final MatchEventRepository events;
    private final MatchGroupContinuationPolicy continuationPolicy;
    private final ApplicationEventPublisher publisher;

    public MatchLeaveService(
            Clock clock,
            MatchGroupRepository groups,
            MatchGroupMemberRepository groupMembers,
            MatchEventRepository events,
            MatchGroupContinuationPolicy continuationPolicy,
            ApplicationEventPublisher publisher
    ) {
        this.clock = clock;
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.events = events;
        this.continuationPolicy = continuationPolicy;
        this.publisher = publisher;
    }

    @Transactional
    public MatchCancellationResponse leave(long memberId) {
        List<MatchGroup> activeGroups = groups.findActiveByMemberIdForUpdate(memberId);
        if (activeGroups.size() != 1) {
            throw new BusinessException(ErrorCode.MATCHING_LEAVE_NOT_ALLOWED);
        }
        MatchGroup group = activeGroups.get(0);
        List<MatchGroupMember> members = groupMembers.findAllByGroupIdForUpdate(group.getId());
        MatchGroupMember actor = members.stream()
                .filter(candidate -> candidate.getMemberId() == memberId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCHING_LEAVE_NOT_ALLOWED));

        // 도착했고 <b>만남이 성립한</b> 사람만 이 경로를 쓴다. 둘 중 하나라도 아니면 참여 취소
        // (MatchCancellationService)가 맡는다. 이 경로에 페널티가 없기 때문에 경계가 중요하다.
        // 성립 전에도 무패널티로 나갈 수 있으면, 도착 버튼을 눌렀다 나가는 것만으로 취소
        // 페널티를 피할 수 있고 오고 있는 사람에 대한 책임이 사라진다.
        if (!"ARRIVED".equals(actor.getStatus()) || !continuationPolicy.meetingHeld(members)) {
            throw new BusinessException(ErrorCode.MATCHING_LEAVE_NOT_ALLOWED);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Long> notified = activeMemberIds(members);
        actor.leaveEarly(now);
        events.save(MatchEvent.memberLeft(group.getId(), group.getAttemptId(), memberId, now));

        List<MatchGroupMember> remaining = members.stream().filter(this::isActive).toList();
        String cancellationReason = continuationPolicy.cancellationReason(members);
        boolean cancelled = cancellationReason != null;
        if (cancelled) {
            group.cancel(cancellationReason, now);
            remaining.forEach(member -> member.leave(now));
            events.save(MatchEvent.matchCancelled(
                    group.getId(), group.getAttemptId(), cancellationReason, now));
        }
        groupMembers.flush();
        groups.flush();
        events.flush();
        publisher.publishEvent(new MatchingStateChangedEvent(
                notified, cancelled ? "MATCH_CANCELLED" : "MEMBER_LEFT", now, memberId));
        // 그룹 snapshot을 돌려주지 않는다. 나간 사람은 더 이상 활성 참여자가 아니라 조회에서
        // 걸러지고, 화면도 이 응답을 받으면 상태방을 떠나므로 방 내용이 필요 없다.
        return new MatchCancellationResponse(
                group.getId(), "LEFT", group.getStatus(), !cancelled,
                cancelled ? 0 : remaining.size());
    }

    private List<Long> activeMemberIds(List<MatchGroupMember> members) {
        return members.stream().filter(this::isActive).map(MatchGroupMember::getMemberId).toList();
    }

    private boolean isActive(MatchGroupMember member) {
        return "JOINED".equals(member.getStatus())
                || "ARRIVAL_TIME_SELECTED".equals(member.getStatus())
                || "ARRIVED".equals(member.getStatus());
    }
}
