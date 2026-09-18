package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchEvent;
import com.survey.meetorsolo.domain.matching.entity.MatchGroup;
import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import com.survey.meetorsolo.domain.matching.event.MatchCompletedEvent;
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

/**
 * 만남 시간이 끝난 그룹을 완료 또는 취소로 닫는다({@code docs/19} 4.11.2).
 *
 * <p>예전에는 그룹을 닫는 자리가 {@code MatchArrivalService}뿐이었고, 마지막 도착자가 버튼을
 * 누르는 순간 완료됐다. 그래서 두 가지가 동시에 잘못됐다.
 *
 * <ol>
 *   <li>만남이 시작되는 시점에 상태방이 사라지고 매너온도 보상이 지급됐다.</li>
 *   <li><b>닫히지 않는 그룹이 남았다.</b> 3명 중 2명이 도착하고 1명이 노쇼하면, 도착 시점에는
 *       노쇼가 아직 {@code JOINED}라 완료되지 않는다. 30분 뒤 노쇼 배치가 그 한 명을
 *       {@code NO_SHOW}로 바꾸면 남은 두 명은 모두 {@code ARRIVED}인데, 노쇼 후보 조회는
 *       {@code JOINED}/{@code ARRIVAL_TIME_SELECTED}가 있는 그룹만 찾으므로 이 그룹은 다시
 *       조회되지 않는다. 결과적으로 그룹이 {@code IN_PROGRESS}로 영구히 남아 참가자는 보상도
 *       받지 못하고 {@code existsActiveByMemberId} 때문에 새 매칭 신청도 막혔다.</li>
 * </ol>
 *
 * <p>도착자가 {@link MatchMeetingWindowPolicy#MINIMUM_ARRIVED_MEMBERS}명 이상이면 완료로 닫고
 * <b>도착한 사람에게만</b> 보상한다. 그 미만이면 만남이 성립하지 않은 것으로 보고 취소한다.
 */
@Service
public class MatchMeetingCloseGroupService {

    private final MatchGroupRepository groups;
    private final MatchGroupMemberRepository groupMembers;
    private final MatchEventRepository events;
    private final ApplicationEventPublisher publisher;

    public MatchMeetingCloseGroupService(
            MatchGroupRepository groups,
            MatchGroupMemberRepository groupMembers,
            MatchEventRepository events,
            ApplicationEventPublisher publisher
    ) {
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.events = events;
        this.publisher = publisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean process(long groupId, OffsetDateTime now) {
        MatchGroup group = groups.tryLockActiveById(groupId).orElse(null);
        if (group == null
                || MatchMeetingWindowPolicy.closesAt(group.getConfirmedAt()).isAfter(now)) {
            return false;
        }
        List<MatchGroupMember> members = groupMembers.findAllByGroupIdForUpdate(groupId);

        // 아직 도착도 노쇼도 아닌 구성원이 남아 있으면 노쇼 배치가 먼저 처리해야 한다.
        // 여기서 노쇼를 대신 찍으면 penalty 적용까지 복제하게 되므로 다음 주기로 미룬다.
        // 도착 마감(30분)이 만남 종료(1시간)보다 앞서므로 정상 운영에서는 한 주기면 정리된다.
        boolean pendingArrival = members.stream().anyMatch(this::isWaitingForArrival);
        if (pendingArrival) {
            return false;
        }

        // 판정 기준은 현재 상태가 아니라 arrived_at이다. 먼저 나간 사람(LEFT + left_at)도
        // 실제로 그 자리에 왔으므로 만남 성립 인원에 든다. 머문 시간으로 가르지 않기로 했다.
        List<MatchGroupMember> arrived = members.stream()
                .filter(member -> member.getArrivedAt() != null)
                .toList();
        List<Long> arrivedMemberIds = arrived.stream()
                .map(MatchGroupMember::getMemberId)
                .toList();
        if (arrivedMemberIds.isEmpty()) {
            return false;
        }

        boolean meetingHeld = MatchMeetingWindowPolicy.isMeetingHeld(arrived.size());
        if (meetingHeld) {
            group.complete(now);
            arrived.stream().filter(member -> "ARRIVED".equals(member.getStatus()))
                    .forEach(member -> member.complete(now));
            events.save(MatchEvent.matchCompleted(groupId, group.getAttemptId(), now));
        } else {
            group.cancel(MatchMeetingWindowPolicy.INSUFFICIENT_ARRIVALS, now);
            arrived.stream().filter(member -> "ARRIVED".equals(member.getStatus()))
                    .forEach(member -> member.leave(now));
            events.save(MatchEvent.matchCancelled(
                    groupId, group.getAttemptId(),
                    MatchMeetingWindowPolicy.INSUFFICIENT_ARRIVALS, now));
        }
        groupMembers.flush();
        groups.flush();
        events.flush();
        publisher.publishEvent(new MatchingStateChangedEvent(
                arrivedMemberIds, meetingHeld ? "MATCH_COMPLETED" : "MATCH_CANCELLED", now));
        if (meetingHeld) {
            // 매너온도 보상은 AFTER_COMMIT에서 별도 transaction으로 지급한다. 보상 실패가
            // 완료를 롤백하면 안 되고, 여기서 members까지 잠그면 members -> match_groups 순으로
            // 잠그는 탈퇴 경로와 교차 deadlock이 생긴다(MannerTemperatureRewardService 참고).
            publisher.publishEvent(new MatchCompletedEvent(groupId, arrivedMemberIds, now));
        }
        return true;
    }

    private boolean isWaitingForArrival(MatchGroupMember member) {
        return "JOINED".equals(member.getStatus())
                || "ARRIVAL_TIME_SELECTED".equals(member.getStatus());
    }
}
