package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.entity.MatchCooldown;
import com.survey.meetorsolo.domain.matching.service.MatchCompletionLockPolicy.CompletionLock;
import com.survey.meetorsolo.domain.member.policy.MannerTemperaturePolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;

public record MatchingRestrictionResponse(
        /** 내부 운영 값이라 화면에 표시하지 않는다. 노쇼 쿨타임 판정에 쓰인다. */
        int penaltyScore,
        /** 본인의 매너온도(docs/19 4.9). 매칭 화면에 표시한다. */
        BigDecimal mannerTemperature,
        CooldownResponse cooldown,
        CompletionLockResponse completionLock,
        /** 매너온도 매칭 제한(docs/19 4.9 PR C). */
        TemperatureLimitResponse temperatureLimit,
        OffsetDateTime serverNow
) {
    public static MatchingRestrictionResponse of(
            int penaltyScore,
            BigDecimal mannerTemperature,
            MatchCooldown cooldown,
            CompletionLock completionLock,
            OffsetDateTime now
    ) {
        return new MatchingRestrictionResponse(
                penaltyScore,
                mannerTemperature,
                cooldown == null ? CooldownResponse.inactive() : activeCooldown(cooldown, now),
                CompletionLockResponse.from(completionLock),
                TemperatureLimitResponse.of(mannerTemperature),
                now
        );
    }

    private static CooldownResponse activeCooldown(MatchCooldown cooldown, OffsetDateTime now) {
        long remainingSeconds = Math.max(0, Duration.between(now, cooldown.getExpiresAt()).toSeconds());
        return new CooldownResponse(
                true,
                cooldown.getReason(),
                cooldown.getStartsAt(),
                cooldown.getExpiresAt(),
                remainingSeconds
        );
    }

    public record CooldownResponse(
            boolean active,
            String reason,
            OffsetDateTime startsAt,
            OffsetDateTime expiresAt,
            long remainingSeconds
    ) {
        private static CooldownResponse inactive() {
            return new CooldownResponse(false, null, null, null, 0);
        }
    }

    /**
     * 매너온도 제한 상태.
     *
     * <p>사유(신고)를 담지 않는다. 낮은 온도는 곧 "신고를 받았다"이고, 그것을 응답에 적으면
     * 화면이 어떻게 쓰든 노출 경로가 생긴다(docs/19 4.8 신고자 보호). 대신 <b>기준값</b>을 주어
     * 화면이 "얼마나 모자란지"와 회복 방법을 안내할 수 있게 한다.
     *
     * <p>남은 시간을 주지 않는 이유도 같은 맥락이다. 회복은 만남 완료와 시간 경과 두 경로에
     * 달려 있어 확정된 해제 시각이 없다. 쿨타임처럼 카운트다운을 보여주면 틀린 약속이 된다.
     */
    public record TemperatureLimitResponse(
            boolean active,
            BigDecimal minimumTemperature
    ) {
        private static TemperatureLimitResponse of(BigDecimal mannerTemperature) {
            return new TemperatureLimitResponse(
                    !MannerTemperaturePolicy.matchingAllowed(mannerTemperature),
                    MannerTemperaturePolicy.MATCHING_MINIMUM
            );
        }
    }

    public record CompletionLockResponse(
            boolean active,
            String reason,
            Long groupId,
            OffsetDateTime startsAt,
            OffsetDateTime expiresAt,
            long remainingSeconds
    ) {
        private static CompletionLockResponse from(CompletionLock lock) {
            return new CompletionLockResponse(
                    lock.active(), lock.reason(), lock.groupId(), lock.startsAt(),
                    lock.expiresAt(), lock.remainingSeconds()
            );
        }
    }
}
