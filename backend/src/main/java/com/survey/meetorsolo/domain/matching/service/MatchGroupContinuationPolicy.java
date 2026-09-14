package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 남은 인원으로 만남을 이어갈 수 있는지 판정한다.
 *
 * <p>취소·노쇼로 구성원이 줄었을 때 그룹을 유지할지 종료할지를 여기서 정한다. 판정 시점은
 * 참여 취소({@code MatchCancellationService})와 도착 마감 노쇼 처리
 * ({@code MatchNoShowGroupService}) 두 곳이다.
 */
@Component
public class MatchGroupContinuationPolicy {

    /** 종료해야 하면 사유를, 이어갈 수 있으면 {@code null}을 돌려준다. */
    public String cancellationReason(List<MatchGroupMember> active) {
        if (active.size() <= 1) return "INSUFFICIENT_ACTIVE_MEMBERS";
        if (active.size() == 2 && !allowsTwo(active) && !allArrived(active)) {
            return "MINIMUM_TWO_NOT_ALLOWED";
        }
        return null;
    }

    private boolean allowsTwo(List<MatchGroupMember> active) {
        return active.stream().allMatch(member -> Boolean.TRUE.equals(member.getAllowMinimumTwo()));
    }

    /**
     * 남은 두 사람이 모두 만남 장소에 도착했는지.
     *
     * <p><b>도착했다면 2인 진행에 동의하지 않았더라도 종료하지 않는다</b>({@code docs/19} 4.11.2).
     * "2명은 싫어요"({@code allowMinimumTwo = false})는 <b>만나기 전</b>의 의사다. 판정 시점인
     * 도착 마감에는 둘 다 이미 현장에 나와 서로 마주 본 상태이고, 그 자리에서 앱이 그룹을
     * 취소해도 만난 사실은 달라지지 않는다. 오히려 취소하면 끝까지 나온 두 사람이 매너온도
     * 보상을 받지 못한다.
     *
     * <p>한 명이라도 오지 않았다면 예전대로 종료한다. 그때는 아직 만남이 시작되지 않았으므로
     * 사전에 받아둔 의사가 그대로 유효하다.
     */
    private boolean allArrived(List<MatchGroupMember> active) {
        return active.stream().allMatch(member -> "ARRIVED".equals(member.getStatus()));
    }
}
