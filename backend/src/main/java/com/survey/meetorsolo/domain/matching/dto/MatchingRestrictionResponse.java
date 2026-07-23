package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.entity.MatchCooldown;
import java.time.Duration;
import java.time.OffsetDateTime;

public record MatchingRestrictionResponse(
        int penaltyScore,
        CooldownResponse cooldown
) {
    public static MatchingRestrictionResponse of(
            int penaltyScore,
            MatchCooldown cooldown,
            OffsetDateTime now
    ) {
        if (cooldown == null) {
            return new MatchingRestrictionResponse(penaltyScore, CooldownResponse.inactive());
        }
        long remainingSeconds = Math.max(0, Duration.between(now, cooldown.getExpiresAt()).toSeconds());
        return new MatchingRestrictionResponse(
                penaltyScore,
                new CooldownResponse(
                        true,
                        cooldown.getReason(),
                        cooldown.getStartsAt(),
                        cooldown.getExpiresAt(),
                        remainingSeconds
                )
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
}
