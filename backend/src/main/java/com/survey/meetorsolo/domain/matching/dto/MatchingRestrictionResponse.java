package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.entity.MatchCooldown;
import com.survey.meetorsolo.domain.matching.service.MatchCompletionLockPolicy.CompletionLock;
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
