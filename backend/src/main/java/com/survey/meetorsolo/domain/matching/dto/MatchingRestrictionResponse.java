package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.entity.MatchCooldown;
import com.survey.meetorsolo.domain.matching.service.MatchCompletionLockPolicy.CompletionLock;
import java.time.Duration;
import java.time.OffsetDateTime;

public record MatchingRestrictionResponse(
        int penaltyScore,
        CooldownResponse cooldown,
        CompletionLockResponse completionLock,
        OffsetDateTime serverNow
) {
    public static MatchingRestrictionResponse of(
            int penaltyScore,
            MatchCooldown cooldown,
            CompletionLock completionLock,
            OffsetDateTime now
    ) {
        return new MatchingRestrictionResponse(
                penaltyScore,
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
