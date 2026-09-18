package com.survey.meetorsolo.domain.member.service;

import com.survey.meetorsolo.domain.member.entity.MannerTemperatureEvent;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.policy.MannerTemperaturePolicy;
import com.survey.meetorsolo.domain.member.repository.MannerTemperatureEventRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만남을 끝까지 마친 회원의 매너온도를 올린다({@code docs/19} 4.9).
 *
 * <p><b>후기와 무관하게 참여만으로 오른다.</b> 후기는 상대가 안 써주면 잘 참여한 회원도 못
 * 오르지만, 완료는 본인 행동만으로 결정된다. 조작도 어렵다 — 축제 현장 GPS 체크인, 매칭 성사,
 * 만남 장소 전원 도착이 모두 필요하고 완료 후 1시간 재매칭 잠금이 걸린다.
 *
 * <p><b>완료 transaction에 참여하지 않는다.</b> 호출자는 AFTER_COMMIT handler이고 여기서
 * 새 transaction을 연다. 두 가지 이유다.
 *
 * <ol>
 *   <li>보상 실패가 만남 완료를 롤백하면 안 된다. 온도는 부가 지표이고 완료는 사용자가 실제로
 *       수행한 사실이다.</li>
 *   <li><b>lock 순서.</b> 완료 transaction은 {@code match_groups} → {@code match_group_members}
 *       순으로 잠근다. 같은 transaction에서 {@code members}까지 잠그면,
 *       {@code members} → {@code match_groups} 순으로 잠그는 탈퇴 경로
 *       ({@code MemberWithdrawalService})와 교차 deadlock이 생긴다.</li>
 * </ol>
 */
@Service
public class MannerTemperatureRewardService {

    private static final Logger log = LoggerFactory.getLogger(MannerTemperatureRewardService.class);

    private final MemberRepository members;
    private final MannerTemperatureEventRepository events;

    public MannerTemperatureRewardService(
            MemberRepository members, MannerTemperatureEventRepository events) {
        this.members = members;
        this.events = events;
    }

    /**
     * 완료된 그룹의 참여자에게 보상을 지급하고 실제로 지급된 인원 수를 반환한다.
     *
     * <p>회원마다 별도 transaction을 쓰지 않는다. 같은 그룹은 2~4명이라 한 transaction으로
     * 묶어도 lock 보유 시간이 짧고, 부분 지급 상태를 남기지 않는 편이 이력 해석에 낫다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int rewardCompletedMembers(long groupId, List<Long> memberIds, OffsetDateTime now) {
        int rewarded = 0;
        // deadlock을 피하려고 항상 회원 ID 오름차순으로 잠근다. 완료 그룹의 참여자 목록은
        // 호출자마다 순서가 다를 수 있다.
        for (long memberId : memberIds.stream().sorted().toList()) {
            if (reward(memberId, groupId, now)) {
                rewarded++;
            }
        }
        return rewarded;
    }

    private boolean reward(long memberId, long groupId, OffsetDateTime now) {
        if (events.existsByMemberIdAndEventTypeAndRelatedGroupId(
                memberId, MannerTemperatureEvent.TYPE_MATCH_COMPLETED, groupId)) {
            return false;
        }
        Member member = members.findByIdForUpdate(memberId).orElse(null);
        if (member == null) {
            // 완료 직후 탈퇴한 회원. 보상 대상이 없을 뿐 오류가 아니다.
            return false;
        }
        BigDecimal before = member.getMannerTemperature();
        BigDecimal applied = member.increaseMannerTemperature(
                MannerTemperaturePolicy.MATCH_COMPLETED_DELTA, MannerTemperaturePolicy.CEILING);
        if (applied.signum() == 0) {
            // 이미 상한이다. 이력을 남기면 "아무 일도 없었던 사건"이 쌓여 보상 횟수를 셀 수 없다.
            log.debug("매너온도가 이미 상한이라 완료 보상을 건너뜁니다. memberId={}, groupId={}",
                    memberId, groupId);
            return false;
        }
        events.save(MannerTemperatureEvent.matchCompleted(
                memberId, applied, before, member.getMannerTemperature(), groupId, now));
        return true;
    }
}
