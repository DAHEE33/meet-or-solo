package com.survey.meetorsolo.domain.safety.report.policy;

import java.time.OffsetDateTime;

/**
 * 만남 종료 후 신고 가능 기간 정책이다.
 *
 * <p>접수 API(`MatchReportService`)와 매칭 기록 목록이 같은 기준을 써야 화면에서 신고
 * 가능하다고 표시한 항목이 접수에서 거절되지 않는다. 그래서 상수와 판정을 한 곳에 둔다.
 *
 * <p>기준 시각은 만남이 끝난 시각이다. 정상 종료는 `completed_at`, 취소는 `cancelled_at`이다.
 */
public final class MatchReportWindowPolicy {

    public static final int WINDOW_DAYS = 14;

    private MatchReportWindowPolicy() {
    }

    public static OffsetDateTime reportableUntil(OffsetDateTime terminalAt) {
        return terminalAt == null ? null : terminalAt.plusDays(WINDOW_DAYS);
    }

    public static boolean isReportable(OffsetDateTime terminalAt, OffsetDateTime now) {
        OffsetDateTime until = reportableUntil(terminalAt);
        return until != null && !now.isAfter(until);
    }
}
