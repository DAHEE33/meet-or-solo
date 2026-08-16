package com.survey.meetorsolo.domain.checkin;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class CheckinValidityPolicy {

    public static final Duration VALIDITY = Duration.ofHours(1);

    public OffsetDateTime expiresAt(OffsetDateTime checkedInAt) {
        return checkedInAt.plus(VALIDITY);
    }

    public boolean isValid(OffsetDateTime checkedInAt, OffsetDateTime storedExpiresAt, OffsetDateTime now) {
        OffsetDateTime policyExpiresAt = expiresAt(checkedInAt);
        OffsetDateTime effectiveExpiresAt = storedExpiresAt.isBefore(policyExpiresAt)
                ? storedExpiresAt
                : policyExpiresAt;
        return now.isBefore(effectiveExpiresAt);
    }
}
