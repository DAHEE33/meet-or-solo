package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 남은 인원으로 만남을 이어갈 수 있는지 판정한다.
 *
 * <p>취소·노쇼·이탈로 구성원이 줄었을 때 그룹을 유지할지 종료할지를 여기서 정한다. 판정 시점은
 * 참여 취소({@code MatchCancellationService}), 도착 마감 노쇼 처리
 * ({@code MatchNoShowGroupService}), 먼저 나가기({@code MatchLeaveService}) 세 곳이다.
 */
@Component
public class MatchGroupContinuationPolicy {

    /**
     * 종료해야 하면 사유를, 이어갈 수 있으면 {@code null}을 돌려준다.
     *
     * @param members 그룹의 <b>전체</b> 구성원이다. 활성 구성원만으로는 "이미 만났는가"를 알 수
     *                없어서, 이탈·노쇼로 빠진 사람의 {@code arrived_at}까지 봐야 한다.
     */
    public String cancellationReason(List<MatchGroupMember> members) {
        if (meetingHeld(members)) {
            return null;
        }
        List<MatchGroupMember> active = members.stream().filter(this::isActive).toList();
        if (active.size() <= 1) return "INSUFFICIENT_ACTIVE_MEMBERS";
        if (active.size() == 2 && !allowsTwo(active)) {
            return "MINIMUM_TWO_NOT_ALLOWED";
        }
        return null;
    }

    /**
     * 이미 만남이 성립한 방은 인원이 줄어도 종료하지 않는다({@code docs/19} 4.11).
     *
     * <p>종료 사유 두 가지는 모두 <b>"이 방은 만남이 성립할 수 없다"</b>는 뜻이다. 도착자가
     * {@link MatchMeetingWindowPolicy#MINIMUM_ARRIVED_MEMBERS}명 이상이면 이미 성립했으므로 그
     * 판단 자체가 성립하지 않는다. 여기서 취소하면 만난 사실은 그대로인데 기록만 "취소"로 남고,
     * 끝까지 있던 사람이 매너온도 보상을 잃는다.
     *
     * <p>2인 진행 미동의({@code allowMinimumTwo = false})도 이 조건에 흡수된다. 그 의사는
     * <b>만나기 전</b>의 것이라, 둘 다 도착한 뒤에는 근거가 사라진다.
     */
    private boolean meetingHeld(List<MatchGroupMember> members) {
        return members.stream().filter(member -> member.getArrivedAt() != null).count()
                >= MatchMeetingWindowPolicy.MINIMUM_ARRIVED_MEMBERS;
    }

    private boolean allowsTwo(List<MatchGroupMember> active) {
        return active.stream().allMatch(member -> Boolean.TRUE.equals(member.getAllowMinimumTwo()));
    }

    private boolean isActive(MatchGroupMember member) {
        return "JOINED".equals(member.getStatus())
                || "ARRIVAL_TIME_SELECTED".equals(member.getStatus())
                || "ARRIVED".equals(member.getStatus());
    }
}
