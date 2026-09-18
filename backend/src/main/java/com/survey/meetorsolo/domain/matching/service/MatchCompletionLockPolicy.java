package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchGroup;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class MatchCompletionLockPolicy {

    public static final Duration MATCH_VALIDITY = Duration.ofHours(1);

    public CompletionLock evaluate(MatchGroup completedGroup, OffsetDateTime now) {
        if (completedGroup == null) {
            return CompletionLock.none();
        }
        OffsetDateTime startsAt = completedGroup.getConfirmedAt();
        OffsetDateTime expiresAt = startsAt.plus(MATCH_VALIDITY);
        boolean active = now.isBefore(expiresAt);
        long remainingSeconds = active
                ? Math.max(0, Duration.between(now, expiresAt).toSeconds())
                : 0;
        return new CompletionLock(
                active,
                "MATCH_VALIDITY",
                completedGroup.getId(),
                startsAt,
                expiresAt,
                remainingSeconds
        );
    }

    public record CompletionLock(
            boolean active,
            String reason,
            Long groupId,
            OffsetDateTime startsAt,
            OffsetDateTime expiresAt,
            long remainingSeconds
    ) {
        private static CompletionLock none() {
            return new CompletionLock(false, null, null, null, null, 0);
        }
    }
}
