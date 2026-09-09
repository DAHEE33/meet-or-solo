package com.survey.meetorsolo.domain.inquiry.admin.service;

import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService;
import com.survey.meetorsolo.domain.inquiry.admin.dto.AdminInquiryDetailResponse;
import com.survey.meetorsolo.domain.inquiry.admin.dto.AdminInquiryListItemResponse;
import com.survey.meetorsolo.domain.inquiry.admin.dto.AdminInquiryMemberSummaryResponse;
import com.survey.meetorsolo.domain.inquiry.admin.dto.AdminInquiryPageResponse;
import com.survey.meetorsolo.domain.inquiry.admin.dto.AdminInquiryPaginationResponse;
import com.survey.meetorsolo.domain.inquiry.admin.dto.AdminInquiryTargetStatus;
import com.survey.meetorsolo.domain.inquiry.admin.repository.AdminInquiryRepository;
import com.survey.meetorsolo.domain.inquiry.dto.InquiryMessageResponse;
import com.survey.meetorsolo.domain.inquiry.entity.Inquiry;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryCategory;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryMessage;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryMessageAuthorType;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryPriority;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryStatus;
import com.survey.meetorsolo.domain.inquiry.repository.InquiryMessageRepository;
import com.survey.meetorsolo.domain.inquiry.repository.InquiryRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 문의 목록·상세·답변·상태 변경.
 *
 * <p>구조는 {@code AdminReportService}와 같다 — {@code AdminAuthorizationService.requireAdmin}
 * 후 HMAC 서명 cursor + filter fingerprint로 페이징한다.
 *
 * <p>{@code admin_actions}에는 기록하지 않는다. 그 table의 {@code action_type} CHECK는 제재·신고
 * 처리 값으로 고정되어 있고 문의 답변은 회원 제재가 아니다. 새 값을 넣으려면 기존 CHECK를
 * 수정하는 migration이 필요하다(docs/29 5.5).
 */
@Service
public class AdminInquiryService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final AdminAuthorizationService authorization;
    private final AdminInquiryRepository adminInquiries;
    private final InquiryRepository inquiries;
    private final InquiryMessageRepository messages;
    private final AdminInquiryCursorCodec cursorCodec;
    private final Clock clock;

    public AdminInquiryService(
            AdminAuthorizationService authorization,
            AdminInquiryRepository adminInquiries,
            InquiryRepository inquiries,
            InquiryMessageRepository messages,
            AdminInquiryCursorCodec cursorCodec,
            Clock clock
    ) {
        this.authorization = authorization;
        this.adminInquiries = adminInquiries;
        this.inquiries = inquiries;
        this.messages = messages;
        this.cursorCodec = cursorCodec;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminInquiryPageResponse list(
            long adminMemberId,
            String statusValue,
            String categoryValue,
            String priorityValue,
            String createdFromValue,
            String createdToValue,
            String cursorValue,
            Integer sizeValue
    ) {
        authorization.requireAdmin(adminMemberId);
        AdminInquiryFilter filter =
                filter(statusValue, categoryValue, priorityValue, createdFromValue, createdToValue);
        int size = size(sizeValue);
        AdminInquiryCursorCodec.Cursor cursor = cursorValue == null
                ? null : cursorCodec.decode(cursorValue, filter.fingerprint());

        List<AdminInquiryListItemResponse> fetched =
                adminInquiries.findPage(filter, cursor, size + 1);
        boolean hasNext = fetched.size() > size;
        List<AdminInquiryListItemResponse> page = hasNext
                ? new ArrayList<>(fetched.subList(0, size)) : fetched;
        String nextCursor = hasNext ? nextCursor(page.get(page.size() - 1), filter) : null;

        return new AdminInquiryPageResponse(
                page,
                adminInquiries.countOpen(),
                new AdminInquiryPaginationResponse(size, hasNext, nextCursor));
    }

    @Transactional(readOnly = true)
    public AdminInquiryDetailResponse detail(long adminMemberId, long inquiryId) {
        authorization.requireAdmin(adminMemberId);
        Inquiry inquiry = inquiries.findById(inquiryId).orElseThrow(this::notFound);
        return toDetail(inquiry);
    }

    /**
     * 관리자 답변. 종결된 문의에는 남길 수 없다.
     *
     * <p>헤더를 먼저 잠근다. 잠그지 않으면 다른 관리자의 종결({@code CLOSED} + {@code closed_at})과
     * 겹칠 때 {@code chk_inquiries_closed_at}을 위반하는 조합이 만들어진다(docs/29 5.9).
     */
    @Transactional
    public AdminInquiryDetailResponse answer(long adminMemberId, long inquiryId, String rawBody) {
        authorization.requireAdmin(adminMemberId);
        String body = normalizeBody(rawBody);

        Inquiry inquiry = inquiries.findByIdForUpdate(inquiryId).orElseThrow(this::notFound);
        if (inquiry.getStatus() == InquiryStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INQUIRY_CLOSED);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        messages.save(InquiryMessage.create(
                inquiryId, InquiryMessageAuthorType.ADMIN, adminMemberId, body));
        inquiry.onAdminAnswer(now);
        return toDetail(inquiry);
    }

    /**
     * 상태·우선순위 변경. 둘 다 optional이며 적어도 하나는 있어야 한다.
     *
     * <p>답변 완료({@code ANSWERED})는 이 경로로 지정할 수 없다({@link AdminInquiryTargetStatus}).
     * 답변 없이 상태만 바꾸면 사용자 badge가 답변이 온 것처럼 켜진다.
     */
    @Transactional
    public AdminInquiryDetailResponse update(
            long adminMemberId,
            long inquiryId,
            AdminInquiryTargetStatus targetStatus,
            InquiryPriority targetPriority
    ) {
        authorization.requireAdmin(adminMemberId);
        if (targetStatus == null && targetPriority == null) {
            throw invalid("변경할 status 또는 priority가 필요합니다.");
        }

        Inquiry inquiry = inquiries.findByIdForUpdate(inquiryId).orElseThrow(this::notFound);
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (targetPriority != null) {
            inquiry.changePriority(targetPriority);
        }
        if (targetStatus != null) {
            InquiryStatus target = InquiryStatus.valueOf(targetStatus.name());
            if (inquiry.getStatus() != target) {
                validateTransition(inquiry.getStatus(), target);
                inquiry.changeStatus(target, now);
            }
        }
        return toDetail(inquiry);
    }

    /** {@code CLOSED}는 종단이다. 재개하려면 새 문의를 등록한다(docs/29 5.8). */
    private void validateTransition(InquiryStatus current, InquiryStatus target) {
        boolean allowed = switch (current) {
            case RECEIVED, IN_PROGRESS, ANSWERED ->
                    target == InquiryStatus.IN_PROGRESS || target == InquiryStatus.CLOSED;
            case CLOSED -> false;
        };
        if (!allowed) {
            throw new BusinessException(ErrorCode.ADMIN_INQUIRY_STATUS_CONFLICT);
        }
    }

    private AdminInquiryDetailResponse toDetail(Inquiry inquiry) {
        AdminInquiryMemberSummaryResponse member =
                adminInquiries.findMemberSummary(inquiry.getId()).orElseThrow(this::notFound);
        List<InquiryMessageResponse> thread =
                messages.findByInquiryIdOrderByIdAsc(inquiry.getId()).stream()
                        .map(message -> new InquiryMessageResponse(
                                message.getId(),
                                message.getAuthorType(),
                                message.getBody(),
                                message.getCreatedAt()))
                        .toList();
        return new AdminInquiryDetailResponse(
                inquiry.getId(),
                inquiry.getCategory(),
                inquiry.getTitle(),
                inquiry.getStatus(),
                inquiry.getPriority(),
                member,
                thread,
                inquiry.getLastMessageAt(),
                inquiry.getLastAnsweredAt(),
                inquiry.getCreatedAt(),
                inquiry.getClosedAt());
    }

    private AdminInquiryFilter filter(
            String statusValue,
            String categoryValue,
            String priorityValue,
            String fromValue,
            String toValue
    ) {
        try {
            InquiryStatus status = blank(statusValue) ? null : InquiryStatus.valueOf(statusValue);
            InquiryCategory category =
                    blank(categoryValue) ? null : InquiryCategory.valueOf(categoryValue);
            InquiryPriority priority =
                    blank(priorityValue) ? null : InquiryPriority.valueOf(priorityValue);
            OffsetDateTime from = blank(fromValue) ? null : OffsetDateTime.parse(fromValue);
            OffsetDateTime to = blank(toValue) ? null : OffsetDateTime.parse(toValue);
            if (from != null && to != null && !from.isBefore(to)) {
                throw invalid("createdFrom은 createdTo보다 이전이어야 합니다.");
            }
            return new AdminInquiryFilter(status, category, priority, from, to);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalid("문의 목록 filter 값이 올바르지 않습니다.");
        }
    }

    private int size(Integer value) {
        int size = value == null ? DEFAULT_SIZE : value;
        if (size < 1 || size > MAX_SIZE) {
            throw invalid("size는 1 이상 " + MAX_SIZE + " 이하여야 합니다.");
        }
        return size;
    }

    private String nextCursor(AdminInquiryListItemResponse last, AdminInquiryFilter filter) {
        return cursorCodec.encode(last.createdAt(), last.inquiryId(), filter.fingerprint());
    }

    private static String normalizeBody(String raw) {
        String body = raw == null ? "" : raw.trim();
        if (body.isEmpty() || body.length() > InquiryMessage.BODY_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.ADMIN_INQUIRY_INVALID_REQUEST,
                    "답변은 1~" + InquiryMessage.BODY_MAX_LENGTH + "자여야 합니다.");
        }
        return body;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.ADMIN_INQUIRY_NOT_FOUND);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.ADMIN_INQUIRY_INVALID_REQUEST, message);
    }
}
