package com.survey.meetorsolo.domain.safety.report.admin.service;

import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService;
import com.survey.meetorsolo.domain.safety.report.admin.dto.*;
import com.survey.meetorsolo.domain.safety.report.admin.repository.AdminReportRepository;
import com.survey.meetorsolo.domain.safety.report.dto.MatchReportReasonCode;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminReportService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final AdminAuthorizationService authorization;
    private final AdminReportRepository reports;
    private final AdminReportCursorCodec cursorCodec;
    private final Clock clock;

    public AdminReportService(
            AdminAuthorizationService authorization,
            AdminReportRepository reports,
            AdminReportCursorCodec cursorCodec,
            Clock clock
    ) {
        this.authorization = authorization;
        this.reports = reports;
        this.cursorCodec = cursorCodec;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminReportPageResponse list(
            long adminMemberId,
            String statusValue,
            String reasonValue,
            String createdFromValue,
            String createdToValue,
            String cursorValue,
            Integer sizeValue
    ) {
        authorization.requireAdmin(adminMemberId);
        AdminReportFilter filter = filter(statusValue, reasonValue, createdFromValue, createdToValue);
        int size = size(sizeValue);
        AdminReportCursorCodec.Cursor cursor = cursorValue == null
                ? null : cursorCodec.decode(cursorValue, filter.fingerprint());
        List<AdminReportDetailResponse> fetched = reports.findPage(filter, cursor, size + 1);
        boolean hasNext = fetched.size() > size;
        List<AdminReportDetailResponse> page = hasNext
                ? new ArrayList<>(fetched.subList(0, size)) : fetched;
        String nextCursor = hasNext ? nextCursor(page.get(page.size() - 1), filter) : null;
        return new AdminReportPageResponse(
                page.stream().map(this::toListItem).toList(),
                new AdminReportPaginationResponse(size, hasNext, nextCursor));
    }

    @Transactional(readOnly = true)
    public AdminReportDetailResponse detail(long adminMemberId, long reportId) {
        authorization.requireAdmin(adminMemberId);
        return reports.findDetail(reportId).orElseThrow(this::notFound);
    }

    @Transactional
    public AdminReportDetailResponse changeStatus(
            long adminMemberId, long reportId, AdminReportTargetStatus targetStatus) {
        authorization.requireAdmin(adminMemberId);
        AdminReportDetailResponse current = reports.findDetailForUpdate(reportId)
                .orElseThrow(this::notFound);
        AdminReportStatus target = AdminReportStatus.valueOf(targetStatus.name());
        if (current.status() == target) {
            return current;
        }
        validateTransition(current.status(), target);

        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean terminal = target == AdminReportStatus.RESOLVED || target == AdminReportStatus.REJECTED;
        if (reports.updateStatus(reportId, target, now, terminal) != 1) {
            throw new BusinessException(ErrorCode.ADMIN_REPORT_STATUS_CONFLICT);
        }
        if (terminal) {
            reports.insertAdminAction(
                    adminMemberId,
                    current.reportedMember().memberId(),
                    reportId,
                    target == AdminReportStatus.RESOLVED ? "REPORT_RESOLVE" : "REPORT_REJECT",
                    now);
        }
        return reports.findDetail(reportId).orElseThrow(this::notFound);
    }

    private void validateTransition(AdminReportStatus current, AdminReportStatus target) {
        boolean allowed = current == AdminReportStatus.SUBMITTED
                && (target == AdminReportStatus.REVIEWING
                    || target == AdminReportStatus.RESOLVED
                    || target == AdminReportStatus.REJECTED)
                || current == AdminReportStatus.REVIEWING
                && (target == AdminReportStatus.RESOLVED || target == AdminReportStatus.REJECTED);
        if (!allowed) {
            throw new BusinessException(ErrorCode.ADMIN_REPORT_STATUS_CONFLICT);
        }
    }

    private AdminReportFilter filter(
            String statusValue, String reasonValue, String fromValue, String toValue) {
        try {
            AdminReportStatus status = blank(statusValue) ? null : AdminReportStatus.valueOf(statusValue);
            MatchReportReasonCode reason = blank(reasonValue) ? null : MatchReportReasonCode.valueOf(reasonValue);
            OffsetDateTime from = blank(fromValue) ? null : OffsetDateTime.parse(fromValue);
            OffsetDateTime to = blank(toValue) ? null : OffsetDateTime.parse(toValue);
            if (from != null && to != null && !from.isBefore(to)) {
                throw invalid("createdFrom은 createdTo보다 이전이어야 합니다.");
            }
            return new AdminReportFilter(status, reason, from, to);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalid("신고 목록 filter 값이 올바르지 않습니다.");
        }
    }

    private int size(Integer value) {
        int size = value == null ? DEFAULT_SIZE : value;
        if (size < 1 || size > MAX_SIZE) {
            throw invalid("size는 1 이상 100 이하여야 합니다.");
        }
        return size;
    }

    private String nextCursor(AdminReportDetailResponse last, AdminReportFilter filter) {
        return cursorCodec.encode(last.createdAt(), last.reportId(), filter.fingerprint());
    }

    private AdminReportListItemResponse toListItem(AdminReportDetailResponse detail) {
        return new AdminReportListItemResponse(
                detail.reportId(),
                detail.group() == null ? null : detail.group().groupId(),
                detail.reasonCode(),
                detail.status(),
                detail.reporter(),
                detail.reportedMember(),
                detail.createdAt(),
                detail.updatedAt());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.ADMIN_REPORT_NOT_FOUND);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.ADMIN_REPORT_INVALID_REQUEST, message);
    }
}
