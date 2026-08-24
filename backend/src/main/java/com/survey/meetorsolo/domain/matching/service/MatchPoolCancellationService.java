package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchCooldown;
import com.survey.meetorsolo.domain.matching.entity.MatchPenaltyEvent;
import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import com.survey.meetorsolo.domain.matching.repository.MatchCooldownRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchPenaltyEventRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchPoolCancellationService {

    private static final String COOLDOWN_REASON = "POOL_CANCEL";

    private final Clock clock;
    private final MatchPoolRepository pools;
    private final MatchCooldownRepository cooldowns;
    private final MatchPenaltyEventRepository penaltyEvents;
    private final MemberRepository members;
    private final JdbcTemplate jdbc;

    public MatchPoolCancellationService(
            Clock clock,
            MatchPoolRepository pools,
            MatchCooldownRepository cooldowns,
            MatchPenaltyEventRepository penaltyEvents,
            MemberRepository members,
            JdbcTemplate jdbc
    ) {
        this.clock = clock;
        this.pools = pools;
        this.cooldowns = cooldowns;
        this.penaltyEvents = penaltyEvents;
        this.members = members;
        this.jdbc = jdbc;
    }

    @Transactional
    public MatchPoolCancellationResult cancel(long memberId) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        MatchPool pool = pools.findActiveCancellablePoolForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MATCHING_RESOURCE_NOT_FOUND,
                        "취소할 활성 매칭풀이 없습니다."));

        String currentStatus = pool.getStatus();

        if (MatchPool.STATUS_PROPOSED.equals(currentStatus)) {
            throw new BusinessException(
                    ErrorCode.MATCHING_CONFLICT,
                    "매칭 제안이 진행 중입니다. 제안 응답으로 처리해주세요.");
        }

        pool.cancelByUser(now);

        if (cooldowns.existsByRelatedPoolId(pool.getId())) {
            return new MatchPoolCancellationResult(pool.getId(), "CANCELLED");
        }

        // 현재 체크인의 checked_in_at 이후 취소 횟수로 escalation 판정
        OffsetDateTime checkinStart = findCheckedInAt(pool.getCheckinId());
        int previousCount = cooldowns.countByReasonSince(memberId, COOLDOWN_REASON, checkinStart);
        int currentCount = previousCount + 1;

        Duration cooldownDuration = decideCooldownDuration(currentCount);
        int scoreDelta = decideScoreDelta(currentCount);

        cooldowns.expireElapsedActive(memberId, now);
        cooldowns.save(MatchCooldown.activeForPool(
                memberId, COOLDOWN_REASON, pool.getId(),
                now, now.plus(cooldownDuration)));

        if (scoreDelta > 0 && !penaltyEvents.existsByRelatedPoolId(pool.getId())) {
            penaltyEvents.save(MatchPenaltyEvent.forPoolCancel(
                    memberId, scoreDelta,
                    "매칭 탐색 자발적 취소 (체크인 내 " + currentCount + "회)",
                    pool.getId(), now));
            members.increasePenaltyScore(memberId, scoreDelta);
        }

        return new MatchPoolCancellationResult(pool.getId(), "CANCELLED");
    }

    private OffsetDateTime findCheckedInAt(long checkinId) {
        return jdbc.queryForObject(
                "SELECT checked_in_at FROM festival_checkins WHERE id = ?",
                OffsetDateTime.class,
                checkinId
        );
    }

    private Duration decideCooldownDuration(int count) {
        if (count <= 1) return Duration.ofSeconds(20);
        if (count == 2) return Duration.ofMinutes(1);
        if (count == 3) return Duration.ofMinutes(5);
        return Duration.ofMinutes(10);
    }

    private int decideScoreDelta(int count) {
        return count >= 3 ? 1 : 0;
    }
}
