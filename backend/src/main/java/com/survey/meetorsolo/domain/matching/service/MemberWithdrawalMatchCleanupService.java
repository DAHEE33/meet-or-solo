package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchEvent;
import com.survey.meetorsolo.domain.matching.entity.MatchGroup;
import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import com.survey.meetorsolo.domain.matching.event.MatchingStateChangedEvent;
import com.survey.meetorsolo.domain.matching.repository.MatchEventRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchProposalRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 회원의 진행 중 매칭 정리({@code docs/19} 4.4).
 *
 * <p><b>탈퇴를 거부하지 않는다.</b> 관리자 제재는 활성 매칭이 있으면
 * {@code ADMIN_MEMBER_ACTIVE_MATCH_CONFLICT}로 거부하지만, 같은 방식을 탈퇴에 쓰면
 * "만남이 확정된 사람은 탈퇴할 수 없다"가 되어 개인정보 삭제 요구와 충돌한다.
 *
 * <p><b>penalty와 cooldown을 매기지 않는다.</b> 부과 대상이 익명화되므로 의미가 없다.
 * "탈퇴로 노쇼 penalty를 피한다"는 우회는 penalty가 아니라 재가입 정책
 * ({@link com.survey.meetorsolo.domain.member.service.MemberRejoinPolicy})이 막는다.
 *
 * <p><b>새 이벤트 타입을 만들지 않는다.</b> 남은 그룹원에게는 기존
 * {@code MEMBER_CANCELLED}/{@code MATCH_CANCELLED}를 그대로 보낸다. {@code MatchRoomPage}가
 * 이미 처리하는 값이라 프론트엔드를 건드릴 필요가 없다.
 */
@Service
public class MemberWithdrawalMatchCleanupService {

    /** {@code match_group_members.cancel_reason}에 남기는 값. 컬럼 길이는 100이다. */
    private static final String CANCEL_REASON = "WITHDRAWN";

    private final MatchPoolRepository pools;
    private final MatchProposalRepository proposals;
    private final MatchGroupRepository groups;
    private final MatchGroupMemberRepository groupMembers;
    private final MatchEventRepository events;
    private final MatchGroupContinuationPolicy continuationPolicy;
    private final ApplicationEventPublisher publisher;

    public MemberWithdrawalMatchCleanupService(
            MatchPoolRepository pools,
            MatchProposalRepository proposals,
            MatchGroupRepository groups,
            MatchGroupMemberRepository groupMembers,
            MatchEventRepository events,
            MatchGroupContinuationPolicy continuationPolicy,
            ApplicationEventPublisher publisher
    ) {
        this.pools = pools;
        this.proposals = proposals;
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.events = events;
        this.continuationPolicy = continuationPolicy;
        this.publisher = publisher;
    }

    /**
     * 진행 중 그룹, 제안, pool을 정리한다.
     *
     * <p>{@code MANDATORY}로 둬서 탈퇴 transaction 안에서만 실행되게 한다. 별도 transaction으로
     * 새면 익명화는 롤백됐는데 매칭만 취소된 상태가 남을 수 있다.
     *
     * <p>그룹을 먼저 정리하고 pool을 나중에 취소한다. 순서가 바뀌면 그룹 정리 중 재진입한
     * pool이 남는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cleanUp(long memberId, OffsetDateTime now) {
        leaveActiveGroups(memberId, now);
        proposals.expireSentProposalsOnWithdrawal(memberId, now);
        pools.cancelActivePoolsOnWithdrawal(memberId, now);
    }

    private void leaveActiveGroups(long memberId, OffsetDateTime now) {
        List<MatchGroup> activeGroups = groups.findActiveByMemberIdForUpdate(memberId);
        for (MatchGroup group : activeGroups) {
            List<MatchGroupMember> members = groupMembers.findAllByGroupIdForUpdate(group.getId());
            MatchGroupMember actor = members.stream()
                    .filter(member -> member.getMemberId() == memberId)
                    .filter(this::isActive)
                    .findFirst()
                    .orElse(null);
            if (actor == null) {
                continue;
            }
            List<Long> notified = members.stream()
                    .filter(this::isActive)
                    .map(MatchGroupMember::getMemberId)
                    .filter(id -> id != memberId)
                    .toList();

            // 도착 마감 검사를 하지 않는다. MatchCancellationService는 마감이 지나면 거부하는데
            // 탈퇴가 그 시점에 걸려 실패하면 회원이 탈퇴할 수 없게 된다.
            actor.cancel(CANCEL_REASON, now);
            events.save(MatchEvent.memberCancelled(
                    group.getId(), group.getAttemptId(), memberId, CANCEL_REASON, now));
            finishGroupIfNeeded(group, members, now);

            groupMembers.flush();
            groups.flush();
            events.flush();
            if (!notified.isEmpty()) {
                publisher.publishEvent(new MatchingStateChangedEvent(
                        notified,
                        "CANCELLED".equals(group.getStatus()) ? "MATCH_CANCELLED" : "MEMBER_CANCELLED",
                        now));
            }
        }
    }

    private void finishGroupIfNeeded(
            MatchGroup group, List<MatchGroupMember> members, OffsetDateTime now) {
        List<MatchGroupMember> active = members.stream().filter(this::isActive).toList();
        String reason = continuationPolicy.cancellationReason(active);
        if (reason == null) {
            return;
        }
        group.cancel(reason, now);
        active.forEach(member -> member.leave(now));
        events.save(MatchEvent.matchCancelled(group.getId(), group.getAttemptId(), reason, now));
    }

    private boolean isActive(MatchGroupMember member) {
        return "JOINED".equals(member.getStatus())
                || "ARRIVAL_TIME_SELECTED".equals(member.getStatus())
                || "ARRIVED".equals(member.getStatus());
    }
}
