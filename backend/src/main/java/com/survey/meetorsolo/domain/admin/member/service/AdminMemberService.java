package com.survey.meetorsolo.domain.admin.member.service;

import com.survey.meetorsolo.domain.admin.member.dto.*;
import com.survey.meetorsolo.domain.admin.member.event.AdminMemberAccessRevokedEvent;
import com.survey.meetorsolo.domain.admin.member.repository.AdminMemberRepository;
import com.survey.meetorsolo.domain.admin.safety.repository.AdminSafetyAlertRepository;
import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService;
import com.survey.meetorsolo.domain.safety.report.admin.service.ReportConfirmationService;
import com.survey.meetorsolo.domain.auth.repository.RefreshTokenRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMemberService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;
    private final AdminAuthorizationService authorization;
    private final MemberRepository members;
    private final AdminMemberRepository adminMembers;
    private final AdminMemberCursorCodec cursorCodec;
    private final RefreshTokenRepository refreshTokens;
    private final AdminSafetyAlertRepository safetyAlerts;
    private final ReportConfirmationService reportConfirmation;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AdminMemberService(
            AdminAuthorizationService authorization,
            MemberRepository members,
            AdminMemberRepository adminMembers,
            AdminMemberCursorCodec cursorCodec,
            RefreshTokenRepository refreshTokens,
            AdminSafetyAlertRepository safetyAlerts,
            ReportConfirmationService reportConfirmation,
            ApplicationEventPublisher events,
            Clock clock
    ) {
        this.authorization = authorization;
        this.members = members;
        this.adminMembers = adminMembers;
        this.cursorCodec = cursorCodec;
        this.refreshTokens = refreshTokens;
        this.safetyAlerts = safetyAlerts;
        this.reportConfirmation = reportConfirmation;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminMemberPageResponse list(
            long adminMemberId, String queryValue, String statusValue, String roleValue,
            String cursorValue, Integer sizeValue) {
        authorization.requireAdmin(adminMemberId);
        AdminMemberFilter filter = filter(queryValue, statusValue, roleValue);
        int size = size(sizeValue);
        AdminMemberCursorCodec.Cursor cursor = blank(cursorValue) ? null
                : cursorCodec.decode(cursorValue, filter.fingerprint());
        List<AdminMemberListItemResponse> fetched = adminMembers.findPage(filter, cursor, size + 1);
        boolean hasNext = fetched.size() > size;
        List<AdminMemberListItemResponse> page = hasNext
                ? new ArrayList<>(fetched.subList(0, size)) : fetched;
        String next = hasNext ? cursorCodec.encode(
                page.get(page.size() - 1).createdAt(),
                page.get(page.size() - 1).memberId(), filter.fingerprint()) : null;
        return new AdminMemberPageResponse(page, new AdminMemberPaginationResponse(size, hasNext, next));
    }

    @Transactional(readOnly = true)
    public AdminMemberDetailResponse detail(long adminMemberId, long memberId) {
        authorization.requireAdmin(adminMemberId);
        return detail(memberId);
    }

    @Transactional
    public AdminMemberDetailResponse act(
            long adminMemberId, long memberId, String idempotencyKeyValue,
            AdminMemberActionRequest request) {
        var admin = authorization.requireAdmin(adminMemberId);
        UUID idempotencyKey = idempotencyKey(idempotencyKeyValue);
        validateRequest(request);
        adminMembers.lockIdempotencyKey(idempotencyKey);

        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_MEMBER_NOT_FOUND));
        String fingerprint = fingerprint(memberId, request);
        Optional<AdminMemberRepository.ExistingAction> existing =
                adminMembers.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            AdminMemberRepository.ExistingAction action = existing.get();
            if (action.targetMemberId() == memberId
                    && action.actionType().equals(request.action().name())
                    && fingerprint.equals(action.fingerprint())) {
                return detail(memberId);
            }
            throw new BusinessException(ErrorCode.ADMIN_ACTION_IDEMPOTENCY_CONFLICT);
        }

        if (admin.memberId() == memberId || Member.ROLE_ADMIN.equals(member.getRole())) {
            throw new BusinessException(ErrorCode.ADMIN_MEMBER_STATUS_CONFLICT,
                    "관리자 계정은 이 API로 제재할 수 없습니다.");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        member.restoreExpiredSuspension(now);
        if (!member.getStatus().equals(request.expectedStatus().name())) {
            throw new BusinessException(ErrorCode.ADMIN_MEMBER_STATUS_CONFLICT);
        }
        if ((request.action() == AdminMemberActionType.SUSPEND
                || request.action() == AdminMemberActionType.BAN)
                && adminMembers.hasActiveMatching(memberId)) {
            throw new BusinessException(ErrorCode.ADMIN_MEMBER_ACTIVE_MATCH_CONFLICT);
        }

        AdminMemberRepository.LockedReport report = lockReport(memberId, request);
        String beforeStatus = member.getStatus();
        apply(member, request, now);
        if (report != null && adminMembers.markReportActionTaken(report.reportId(), now) != 1) {
            throw new BusinessException(ErrorCode.ADMIN_REPORT_STATUS_CONFLICT);
        }
        adminMembers.insertAction(
                adminMemberId, memberId, report == null ? null : report.reportId(), request,
                idempotencyKey, fingerprint, now, beforeStatus, member.getStatus(),
                member.getSuspendedUntil());
        if (request.action() == AdminMemberActionType.SUSPEND
                || request.action() == AdminMemberActionType.BAN) {
            refreshTokens.revokeByMemberId(memberId, now);
            events.publishEvent(new AdminMemberAccessRevokedEvent(memberId));
            // 제재로 대응이 끝난 안전 알림을 같은 transaction에서 종료해 중복 대응을 막는다.
            safetyAlerts.closeByMemberId(memberId, adminMemberId, now);
        }
        members.flush();
        return detail(memberId);
    }

    private AdminMemberRepository.LockedReport lockReport(
            long memberId, AdminMemberActionRequest request) {
        if (request.reportId() == null) return null;
        if (request.action() == AdminMemberActionType.UNBAN
                || request.action() == AdminMemberActionType.UNSUSPEND) {
            throw invalid("해제 조치에는 신고를 연결할 수 없습니다.");
        }
        var report = adminMembers.findReportForUpdate(request.reportId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_REPORT_NOT_FOUND));
        if (report.reportedMemberId() != memberId || !"RESOLVED".equals(report.status())) {
            throw new BusinessException(ErrorCode.ADMIN_REPORT_STATUS_CONFLICT);
        }
        return report;
    }

    private void apply(Member member, AdminMemberActionRequest request, OffsetDateTime now) {
        try {
            switch (request.action()) {
                case WARNING -> validateWarningStatus(member.getStatus());
                case SUSPEND -> member.suspend(now, now.plus(request.suspensionDuration().duration()));
                case BAN -> member.ban();
                case UNBAN -> member.unban();
                case UNSUSPEND -> member.unsuspend();
            }
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.ADMIN_MEMBER_STATUS_CONFLICT);
        }
    }

    private void validateWarningStatus(String status) {
        if (!Member.STATUS_ACTIVE.equals(status)
                && !Member.STATUS_PROFILE_REQUIRED.equals(status)
                && !Member.STATUS_SUSPENDED.equals(status)) {
            throw new BusinessException(ErrorCode.ADMIN_MEMBER_STATUS_CONFLICT);
        }
    }

    private void validateRequest(AdminMemberActionRequest request) {
        if (request.action() == AdminMemberActionType.SUSPEND && request.suspensionDuration() == null) {
            throw invalid("SUSPEND에는 suspensionDuration이 필요합니다.");
        }
        if (request.action() != AdminMemberActionType.SUSPEND && request.suspensionDuration() != null) {
            throw invalid("suspensionDuration은 SUSPEND에만 사용할 수 있습니다.");
        }
        if (request.reasonNote() != null && containsSensitiveLabel(request.reasonNote())) {
            throw invalid("관리자 사유에는 인증정보나 위치정보를 입력할 수 없습니다.");
        }
    }

    private AdminMemberDetailResponse detail(long memberId) {
        AdminMemberListItemResponse member = adminMembers.findSummary(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_MEMBER_NOT_FOUND));
        Member entity = members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_MEMBER_NOT_FOUND));
        long validReportCount = reportConfirmation.countValidReports(
                memberId, OffsetDateTime.now(clock));
        return new AdminMemberDetailResponse(
                member.memberId(), member.nickname(), member.profileImageUrl(), member.role(),
                member.status(), member.penaltyScore(), member.mannerTemperature(),
                entity.getSuspendedAt(), member.suspendedUntil(), member.createdAt(),
                entity.getLastLoginAt(), validReportCount,
                reportConfirmation.isSafetyReviewRequired(validReportCount),
                adminMembers.findReports(memberId), adminMembers.findActions(memberId));
    }

    private AdminMemberFilter filter(String query, String status, String role) {
        try {
            String normalizedQuery = blank(query) ? null : query.trim();
            if (normalizedQuery != null && normalizedQuery.length() > 50) throw invalid("검색어가 너무 깁니다.");
            AdminMemberStatus parsedStatus = blank(status) ? null : AdminMemberStatus.valueOf(status);
            String parsedRole = blank(role) ? null : role;
            if (parsedRole != null && !Set.of(Member.ROLE_USER, Member.ROLE_ADMIN).contains(parsedRole)) {
                throw invalid("role 값이 올바르지 않습니다.");
            }
            return new AdminMemberFilter(normalizedQuery, parsedStatus, parsedRole);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw invalid("회원 목록 filter 값이 올바르지 않습니다.");
        }
    }

    private int size(Integer value) {
        int size = value == null ? DEFAULT_SIZE : value;
        if (size < 1 || size > MAX_SIZE) throw invalid("size는 1 이상 100 이하여야 합니다.");
        return size;
    }

    private UUID idempotencyKey(String value) {
        try {
            if (blank(value)) throw invalid("Idempotency-Key header가 필요합니다.");
            return UUID.fromString(value);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw invalid("Idempotency-Key는 UUID 형식이어야 합니다.");
        }
    }

    private String fingerprint(long memberId, AdminMemberActionRequest request) {
        String canonical = memberId + "|" + request.action() + "|" + request.reasonCode() + "|"
                + normalize(request.reasonNote()) + "|" + request.suspensionDuration() + "|"
                + request.reportId() + "|" + request.expectedStatus();
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("요청 fingerprint 생성에 실패했습니다.", exception);
        }
    }

    private static boolean containsSensitiveLabel(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("password") || lower.contains("token") || lower.contains("secret")
                || lower.contains("oauth") || lower.contains("gps") || lower.contains("위도")
                || lower.contains("경도") || lower.contains("비밀번호");
    }

    private static String normalize(String value) {
        return blank(value) ? "-" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.ADMIN_MEMBER_INVALID_REQUEST, message);
    }
}
