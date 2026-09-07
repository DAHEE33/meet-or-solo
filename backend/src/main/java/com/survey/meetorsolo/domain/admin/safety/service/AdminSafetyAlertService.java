package com.survey.meetorsolo.domain.admin.safety.service;

import com.survey.meetorsolo.domain.admin.safety.dto.AdminSafetyAlertPageResponse;
import com.survey.meetorsolo.domain.admin.safety.dto.AdminSafetyAlertPaginationResponse;
import com.survey.meetorsolo.domain.admin.safety.dto.AdminSafetyAlertResponse;
import com.survey.meetorsolo.domain.admin.safety.dto.AdminSafetyAlertStatus;
import com.survey.meetorsolo.domain.admin.safety.repository.AdminSafetyAlertRepository;
import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminSafetyAlertService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final AdminAuthorizationService authorization;
    private final AdminSafetyAlertRepository alerts;
    private final AdminSafetyAlertCursorCodec cursorCodec;
    private final Clock clock;

    public AdminSafetyAlertService(
            AdminAuthorizationService authorization,
            AdminSafetyAlertRepository alerts,
            AdminSafetyAlertCursorCodec cursorCodec,
            Clock clock
    ) {
        this.authorization = authorization;
        this.alerts = alerts;
        this.cursorCodec = cursorCodec;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminSafetyAlertPageResponse list(
            long adminMemberId, String statusValue, String cursorValue, Integer sizeValue) {
        authorization.requireAdmin(adminMemberId);
        AdminSafetyAlertStatus status = status(statusValue);
        int size = size(sizeValue);
        String fingerprint = fingerprint(status);
        AdminSafetyAlertCursorCodec.Cursor cursor = blank(cursorValue)
                ? null : cursorCodec.decode(cursorValue, fingerprint);
        List<AdminSafetyAlertResponse> fetched = alerts.findPage(status, cursor, size + 1);
        boolean hasNext = fetched.size() > size;
        List<AdminSafetyAlertResponse> page = hasNext
                ? new ArrayList<>(fetched.subList(0, size)) : fetched;
        String nextCursor = hasNext
                ? cursorCodec.encode(
                        page.get(page.size() - 1).createdAt(),
                        page.get(page.size() - 1).alertId(),
                        fingerprint)
                : null;
        return new AdminSafetyAlertPageResponse(
                page,
                new AdminSafetyAlertPaginationResponse(size, hasNext, nextCursor),
                alerts.countOpen());
    }

    /**
     * 알림을 확인 처리한다. 이미 확인했거나 종료된 알림에 다시 호출하면 현재 상태를
     * 그대로 반환하는 멱등 동작이다.
     */
    @Transactional
    public AdminSafetyAlertResponse acknowledge(long adminMemberId, long alertId) {
        authorization.requireAdmin(adminMemberId);
        AdminSafetyAlertRepository.LockedAlert locked = alerts.findByIdForUpdate(alertId)
                .orElseThrow(this::notFound);
        if (!AdminSafetyAlertStatus.OPEN.name().equals(locked.status())) {
            return alerts.findById(alertId).orElseThrow(this::notFound);
        }
        if (alerts.acknowledge(alertId, adminMemberId, OffsetDateTime.now(clock)) != 1) {
            throw new BusinessException(ErrorCode.ADMIN_SAFETY_ALERT_STATUS_CONFLICT);
        }
        return alerts.findById(alertId).orElseThrow(this::notFound);
    }

    private AdminSafetyAlertStatus status(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            return AdminSafetyAlertStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("status 값이 올바르지 않습니다.");
        }
    }

    private int size(Integer value) {
        int size = value == null ? DEFAULT_SIZE : value;
        if (size < 1 || size > MAX_SIZE) {
            throw invalid("size는 1 이상 100 이하여야 합니다.");
        }
        return size;
    }

    private static String fingerprint(AdminSafetyAlertStatus status) {
        return status == null ? "-" : status.name();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.ADMIN_SAFETY_ALERT_NOT_FOUND);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.ADMIN_SAFETY_ALERT_INVALID_REQUEST, message);
    }
}
