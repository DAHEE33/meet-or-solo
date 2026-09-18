package com.survey.meetorsolo.domain.checkin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class CheckinValidityPolicyTest {

    private final CheckinValidityPolicy policy = new CheckinValidityPolicy();
    private final OffsetDateTime checkedInAt = OffsetDateTime.parse("2026-08-10T10:00:00+09:00");

    @Test
    void 체크인_만료시각은_생성_시점에서_1시간_뒤다() {
        assertThat(policy.expiresAt(checkedInAt)).isEqualTo(checkedInAt.plusHours(1));
    }

    @Test
    void 정확히_1시간_경계에서는_만료다() {
        OffsetDateTime expiresAt = checkedInAt.plusHours(1);

        assertThat(policy.isValid(checkedInAt, expiresAt, expiresAt.minusNanos(1))).isTrue();
        assertThat(policy.isValid(checkedInAt, expiresAt, expiresAt)).isFalse();
    }

    @Test
    void 저장된_만료시각이_더_길어도_정책_상한은_1시간이다() {
        assertThat(policy.isValid(checkedInAt, checkedInAt.plusHours(2), checkedInAt.plusHours(1)))
                .isFalse();
    }
}
