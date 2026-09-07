package com.survey.meetorsolo.domain.safety.report.admin.service;

import com.survey.meetorsolo.domain.admin.safety.repository.AdminSafetyAlertRepository;
import com.survey.meetorsolo.domain.matching.entity.MatchPenaltyEvent;
import com.survey.meetorsolo.domain.matching.repository.MatchPenaltyEventRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.domain.safety.report.admin.repository.AdminReportRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

/**
 * 관리자가 유효하다고 판정한 신고의 penalty·매너온도 적용과 누적 임계 알림.
 *
 * <p>정책 근거는 {@code docs/05_MATCHING_POLICY.md}의
 * "관리자 유효 판정 신고 (REPORT_CONFIRMED)" 절과
 * {@code docs/19_ADMIN_MEMBER_SAFETY_ROADMAP.md} 4.3절이다.
 *
 * <p>별도 Scheduler를 두지 않는다. 신고 누적은 시간 경과가 아니라 관리자 행위로만 변하므로
 * {@code AdminReportService}의 {@code RESOLVED} 전이 transaction 안에서 동기 처리한다.
 */
@Service
public class ReportConfirmationService {

    /** 유효 판정 1건당 penalty score 증가량. NO_SHOW의 +3보다 무겁게 둔다. */
    static final int PENALTY_SCORE_DELTA = 5;
    /** 유효 판정 1건당 매너온도 차감량. */
    static final BigDecimal MANNER_TEMPERATURE_DELTA = new BigDecimal("5.00");
    /** 매너온도 하한. 후기 기능이 없어 상승 경로가 없으므로 무한 하강을 막는다. */
    static final BigDecimal MANNER_TEMPERATURE_FLOOR = new BigDecimal("20.00");
    /** 관리자 알림을 생성하는 누적 유효 신고 임계. */
    static final int ALERT_THRESHOLD = 3;
    /** 누적 집계 window. */
    static final int AGGREGATION_WINDOW_DAYS = 30;

    private static final String PENALTY_REASON = "관리자 유효 판정 신고";

    private final AdminReportRepository reports;
    private final MatchPenaltyEventRepository penaltyEvents;
    private final MemberRepository members;
    private final AdminSafetyAlertRepository alerts;

    public ReportConfirmationService(
            AdminReportRepository reports,
            MatchPenaltyEventRepository penaltyEvents,
            MemberRepository members,
            AdminSafetyAlertRepository alerts
    ) {
        this.reports = reports;
        this.penaltyEvents = penaltyEvents;
        this.members = members;
        this.alerts = alerts;
    }

    /**
     * 유효 판정을 적용한다. 호출자의 transaction에 참여하며 별도 transaction을 열지 않는다.
     *
     * <p>호출 전에 신고 상태가 이미 {@code RESOLVED}로 갱신되어 있어야 한다. 누적 집계가
     * 방금 판정한 신고를 포함해야 하기 때문이다.
     *
     * @param reportedMember 호출자가 {@code SELECT FOR UPDATE}로 잠근 피신고 회원
     */
    public void apply(Member reportedMember, long reportId, OffsetDateTime now) {
        if (penaltyEvents.existsByRelatedReportId(reportId)) {
            return;
        }
        BigDecimal appliedDecrease = reportedMember.decreaseMannerTemperature(
                MANNER_TEMPERATURE_DELTA, MANNER_TEMPERATURE_FLOOR);
        reportedMember.increasePenaltyScore(PENALTY_SCORE_DELTA);
        penaltyEvents.save(MatchPenaltyEvent.forReportConfirmed(
                reportedMember.getId(),
                PENALTY_SCORE_DELTA,
                appliedDecrease.negate(),
                PENALTY_REASON,
                reportId,
                now));
        // JPA 변경을 먼저 반영해 같은 transaction의 JDBC 조회가 최신 값을 보게 한다.
        members.flush();

        long validReportCount = countValidReports(reportedMember.getId(), now);
        if (validReportCount >= ALERT_THRESHOLD) {
            alerts.insertIfAbsent(reportedMember.getId(), reportId, validReportCount, now);
        }
    }

    /** 30일 rolling window 기준 누적 유효 신고 건수. */
    public long countValidReports(long reportedMemberId, OffsetDateTime now) {
        return reports.countValidReportsSince(
                reportedMemberId, now.minusDays(AGGREGATION_WINDOW_DAYS));
    }

    public boolean isSafetyReviewRequired(long validReportCount) {
        return validReportCount >= ALERT_THRESHOLD;
    }
}
