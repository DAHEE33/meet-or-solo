package com.survey.meetorsolo.domain.safety.report.service;

import com.survey.meetorsolo.domain.safety.report.dto.MatchReportReasonCode;
import com.survey.meetorsolo.domain.safety.report.dto.MatchReportResponse;
import com.survey.meetorsolo.domain.safety.report.repository.MatchReportRepository;
import com.survey.meetorsolo.domain.safety.report.repository.MatchReportRepository.GroupSnapshot;
import com.survey.meetorsolo.domain.safety.report.repository.MatchReportRepository.ReportSnapshot;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchReportService {

    private static final int REPORT_WINDOW_DAYS = 30;

    private final MatchReportRepository reports;
    private final Clock clock;

    public MatchReportService(MatchReportRepository reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    @Transactional
    public MatchReportResponse submit(
            long reporterMemberId,
            long groupId,
            long reportedMemberId,
            MatchReportReasonCode reasonCode
    ) {
        if (reporterMemberId == reportedMemberId) {
            throw new BusinessException(ErrorCode.REPORT_INVALID_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        GroupSnapshot group = reports.findGroupForShare(groupId)
                .orElseThrow(this::resourceNotFound);

        if (!reports.existsParticipant(groupId, reporterMemberId)
                || !reports.existsParticipant(groupId, reportedMemberId)) {
            throw resourceNotFound();
        }

        validateReportWindow(group, now);

        ReportSnapshot snapshot = reports.insertIfAbsent(
                        reporterMemberId, reportedMemberId, groupId, reasonCode, now)
                .orElseGet(() -> reports.findExisting(
                                reporterMemberId, reportedMemberId, groupId, reasonCode)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_CONFLICT)));
        return toResponse(snapshot);
    }

    private void validateReportWindow(GroupSnapshot group, OffsetDateTime now) {
        if ("CONFIRMED".equals(group.status()) || "IN_PROGRESS".equals(group.status())) {
            return;
        }

        OffsetDateTime terminalAt;
        if ("COMPLETED".equals(group.status())) {
            terminalAt = group.completedAt();
        } else if ("CANCELLED".equals(group.status())) {
            terminalAt = group.cancelledAt();
        } else {
            throw new BusinessException(ErrorCode.REPORT_CONFLICT);
        }

        if (terminalAt == null) {
            throw new BusinessException(ErrorCode.REPORT_CONFLICT);
        }
        if (now.isAfter(terminalAt.plusDays(REPORT_WINDOW_DAYS))) {
            throw new BusinessException(ErrorCode.REPORT_WINDOW_EXPIRED);
        }
    }

    private BusinessException resourceNotFound() {
        return new BusinessException(ErrorCode.REPORT_RESOURCE_NOT_FOUND);
    }

    private MatchReportResponse toResponse(ReportSnapshot snapshot) {
        return new MatchReportResponse(
                snapshot.reportId(),
                snapshot.groupId(),
                snapshot.reportedMemberId(),
                snapshot.reasonCode(),
                snapshot.status(),
                snapshot.createdAt()
        );
    }
}
