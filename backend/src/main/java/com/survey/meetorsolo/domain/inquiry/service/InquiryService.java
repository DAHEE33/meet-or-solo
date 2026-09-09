package com.survey.meetorsolo.domain.inquiry.service;

import com.survey.meetorsolo.domain.inquiry.dto.InquiryDetailResponse;
import com.survey.meetorsolo.domain.inquiry.dto.InquiryListItemResponse;
import com.survey.meetorsolo.domain.inquiry.dto.InquiryListResponse;
import com.survey.meetorsolo.domain.inquiry.dto.InquiryMessageResponse;
import com.survey.meetorsolo.domain.inquiry.dto.InquiryUnreadCountResponse;
import com.survey.meetorsolo.domain.inquiry.entity.Inquiry;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryCategory;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryMessage;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryMessageAuthorType;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryStatus;
import com.survey.meetorsolo.domain.inquiry.repository.InquiryMessageRepository;
import com.survey.meetorsolo.domain.inquiry.repository.InquiryRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 1:1 문의 등록·조회와 추가 질문.
 *
 * <p>영구제한({@code BANNED}) 회원은 이 서비스에 도달하지 못한다. {@code MemberAccessInterceptor}가
 * {@code /api/**}에서 {@code requireBrowsable}로 이미 막기 때문이다. 그래서 영구제한 회원의
 * 이의제기는 고객센터 이메일 안내를 유지한다(docs/29_MEMBER_INQUIRY_DESIGN.md 2.1).
 *
 * <p>정지({@code SUSPENDED}) 회원은 등록할 수 있어야 한다. 제재 사유를 다툴 수 없으면 제재가
 * 일방적이 되므로 {@code SuspendedActivityPolicy}의 허용 목록에 등재했다(docs/29 2.3).
 *
 * <p>{@code PROFILE_REQUIRED} 회원도 허용한다. 댓글은 표시할 닉네임이 없어 거절하지만
 * (`CONTENT_COMMENT_PROFILE_REQUIRED`), 가입이 막혀서 문의하는 경우가 실제 시나리오다.
 */
@Service
public class InquiryService {

    /**
     * 동시에 답변을 기다릴 수 있는 문의 수.
     *
     * <p><b>한계</b>: 동시 요청 여러 건은 모두 통과할 수 있다. 사람이 반복 등록하는 수준만 막는
     * 완화책이며, 엄격히 막으려면 회원 단위 advisory lock이 필요해 MVP 과잉으로 제외했다
     * (docs/29 5.1, {@code ContentCommentService.MIN_COMMENT_INTERVAL}과 같은 판단).
     */
    static final int MAX_OPEN_INQUIRIES = 3;

    private final InquiryRepository inquiries;
    private final InquiryMessageRepository messages;
    private final Clock clock;

    public InquiryService(
            InquiryRepository inquiries,
            InquiryMessageRepository messages,
            Clock clock
    ) {
        this.inquiries = inquiries;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional
    public InquiryDetailResponse create(
            long memberId, InquiryCategory category, String rawTitle, String rawBody) {
        if (category == null) {
            throw invalid();
        }
        String title = normalize(rawTitle, Inquiry.TITLE_MAX_LENGTH);
        String body = normalize(rawBody, InquiryMessage.BODY_MAX_LENGTH);

        if (inquiries.countOpenByMemberId(memberId) >= MAX_OPEN_INQUIRIES) {
            throw new BusinessException(ErrorCode.INQUIRY_TOO_MANY_OPEN);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        Inquiry inquiry = inquiries.save(Inquiry.create(memberId, category, title, now));
        InquiryMessage message = messages.save(InquiryMessage.create(
                inquiry.getId(), InquiryMessageAuthorType.USER, memberId, body));
        return toDetail(inquiry, List.of(message));
    }

    @Transactional(readOnly = true)
    public InquiryListResponse getMyInquiries(long memberId, int page, int size) {
        Page<Inquiry> found = inquiries.findPageByMemberId(memberId, PageRequest.of(page, size));
        return new InquiryListResponse(
                found.getContent().stream().map(InquiryService::toListItem).toList(),
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages(),
                found.hasNext()
        );
    }

    /**
     * 미확인 답변 수. {@code MyPage} badge 전용이라 목록을 불러오지 않는다.
     * 판정은 {@code Inquiry.hasUnreadAnswer()}와 같은 식이어야 한다(docs/29 4.2).
     */
    @Transactional(readOnly = true)
    public InquiryUnreadCountResponse getUnreadAnswerCount(long memberId) {
        return new InquiryUnreadCountResponse(inquiries.countUnreadAnswersByMemberId(memberId));
    }

    /**
     * 스레드 상세. <b>조회가 {@code memberReadAt}을 갱신하므로 읽기 전용 transaction이 아니다.</b>
     *
     * <p>{@code GET}이 상태를 바꾸는 것은 논쟁 여지가 있지만, 화면이 상세를 열었다는 사실 자체가
     * 읽음이고 별도 {@code PUT .../read}로 호출을 2번으로 늘릴 이유가 약하다. 갱신은 뒤로 가지
     * 않아 반복 호출이 멱등하다(docs/29 5.3).
     */
    @Transactional
    public InquiryDetailResponse getMyInquiry(long memberId, long inquiryId) {
        Inquiry inquiry = requireOwned(memberId, inquiryId);
        inquiry.markReadBy(OffsetDateTime.now(clock));
        return toDetail(inquiry, messages.findByInquiryIdOrderByIdAsc(inquiryId));
    }

    /**
     * 사용자 추가 질문. 종결된 문의에는 남길 수 없다.
     *
     * <p>답변 완료 상태였으면 {@code IN_PROGRESS}로 되돌아간다 — 그러지 않으면 관리자 미처리
     * 목록에 다시 뜨지 않아 재질문이 묻힌다(docs/29 5.4).
     */
    @Transactional
    public InquiryDetailResponse addMessage(long memberId, long inquiryId, String rawBody) {
        String body = normalize(rawBody, InquiryMessage.BODY_MAX_LENGTH);

        // 상태를 바꾸므로 헤더를 먼저 잠근다. 관리자 답변·종결과 겹칠 때 CHECK 위반 조합이
        // 만들어지는 것을 막는다(docs/29 5.9).
        Inquiry inquiry = inquiries.findByIdForUpdate(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
        if (!inquiry.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.INQUIRY_FORBIDDEN);
        }
        if (inquiry.getStatus() == InquiryStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INQUIRY_CLOSED);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        messages.save(InquiryMessage.create(
                inquiryId, InquiryMessageAuthorType.USER, memberId, body));
        inquiry.onUserMessage(now);
        // 자기 발화는 읽은 것으로 본다. 그러지 않으면 추가 질문 직후에도 badge가 남는다.
        inquiry.markReadBy(now);
        return toDetail(inquiry, messages.findByInquiryIdOrderByIdAsc(inquiryId));
    }

    private Inquiry requireOwned(long memberId, long inquiryId) {
        Inquiry inquiry = inquiries.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
        if (!inquiry.isOwnedBy(memberId)) {
            // 남의 문의는 존재 여부를 알려도 얻을 정보가 없어 FORBIDDEN으로 구분한다.
            // 댓글 삭제(CONTENT_COMMENT_FORBIDDEN)와 같은 방식이다.
            throw new BusinessException(ErrorCode.INQUIRY_FORBIDDEN);
        }
        return inquiry;
    }

    private static String normalize(String raw, int maxLength) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw invalid();
        }
        return value;
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INQUIRY_INVALID_REQUEST);
    }

    private static InquiryListItemResponse toListItem(Inquiry inquiry) {
        return new InquiryListItemResponse(
                inquiry.getId(),
                inquiry.getCategory(),
                inquiry.getTitle(),
                inquiry.getStatus(),
                inquiry.hasUnreadAnswer(),
                inquiry.getLastMessageAt(),
                inquiry.getCreatedAt()
        );
    }

    private static InquiryDetailResponse toDetail(Inquiry inquiry, List<InquiryMessage> found) {
        return new InquiryDetailResponse(
                inquiry.getId(),
                inquiry.getCategory(),
                inquiry.getTitle(),
                inquiry.getStatus(),
                found.stream().map(InquiryService::toMessage).toList(),
                inquiry.getCreatedAt(),
                inquiry.getClosedAt()
        );
    }

    private static InquiryMessageResponse toMessage(InquiryMessage message) {
        return new InquiryMessageResponse(
                message.getId(),
                message.getAuthorType(),
                message.getBody(),
                message.getCreatedAt()
        );
    }
}
