package com.survey.meetorsolo.domain.member.service;

import com.survey.meetorsolo.domain.member.entity.MannerTemperatureEvent;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.policy.MannerTemperaturePolicy;
import com.survey.meetorsolo.domain.member.repository.MannerTemperatureEventRepository;
import com.survey.meetorsolo.domain.member.repository.MannerTemperatureRecoveryRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시간이 지나면 매너온도를 조금씩 되돌린다({@code docs/19} 4.9).
 *
 * <p><b>왜 필요한가.</b> 상승 경로가 만남 기반뿐이면 30도 매칭 제한과 원리적으로 충돌한다.
 * 제한에 걸린 회원은 매칭을 못 하고, 매칭을 못 하면 완료도 후기도 없어 영원히 회복하지
 * 못한다. 관리자 수동 조정이 유일한 탈출구가 되어 운영 부담이 사람 손에 얹힌다.
 *
 * <p><b>왜 시작값까지만인가.</b> 상한({@code 42.00})까지 올리면 아무 활동도 하지 않은 회원이
 * 가만히 있다가 상한에 도달해, 지표가 "가입한 지 얼마나 됐나"를 뜻하게 된다. 시작값을 넘는
 * 구간은 실제로 만남을 마쳐야 오른다({@link MannerTemperaturePolicy#timeRecoveryCeiling}).
 *
 * <p>회복 주기는 누적 유효 신고의 집계 window와 같은 30일이다. 신고 카운트는 30일이 지나면
 * 집계에서 빠지는데 온도만 영구 하강으로 남던 비대칭을 없앤다.
 */
@Service
public class MannerTemperatureRecoveryService {

    private final MannerTemperatureRecoveryRepository candidates;
    private final MemberRepository members;
    private final MannerTemperatureEventRepository events;
    private final Clock clock;

    public MannerTemperatureRecoveryService(
            MannerTemperatureRecoveryRepository candidates,
            MemberRepository members,
            MannerTemperatureEventRepository events,
            Clock clock
    ) {
        this.candidates = candidates;
        this.members = members;
        this.events = events;
        this.clock = clock;
    }

    /**
     * 회복 대상을 한 batch 처리하고 실제로 올린 회원 수를 반환한다.
     *
     * <p>회원마다 잠금을 다시 잡고 조건을 다시 확인한다. 후보 조회와 갱신 사이에 신고가
     * 확정되거나 관리자가 온도를 조정했을 수 있다.
     */
    @Transactional
    public int recoverBatch(int batchSize) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime threshold = now.minusDays(MannerTemperaturePolicy.TIME_RECOVERY_INTERVAL_DAYS);
        BigDecimal ceiling = MannerTemperaturePolicy.timeRecoveryCeiling();

        int recovered = 0;
        for (long memberId : candidates.findRecoverableMemberIds(ceiling, threshold, batchSize)) {
            if (recover(memberId, ceiling, now)) {
                recovered++;
            }
        }
        return recovered;
    }

    private boolean recover(long memberId, BigDecimal ceiling, OffsetDateTime now) {
        Member member = members.findByIdForUpdate(memberId).orElse(null);
        if (member == null || member.getMannerTemperature().compareTo(ceiling) >= 0) {
            return false;
        }
        BigDecimal before = member.getMannerTemperature();
        BigDecimal applied = member.increaseMannerTemperature(
                MannerTemperaturePolicy.TIME_RECOVERY_DELTA, ceiling);
        if (applied.signum() == 0) {
            return false;
        }
        events.save(MannerTemperatureEvent.timeRecovery(
                memberId, applied, before, member.getMannerTemperature(), now));
        return true;
    }
}
