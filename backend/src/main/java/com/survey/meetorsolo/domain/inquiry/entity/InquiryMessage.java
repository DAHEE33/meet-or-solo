package com.survey.meetorsolo.domain.inquiry.entity;

import com.survey.meetorsolo.global.time.SeoulDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 문의 스레드의 발화 1건. 사용자 문의와 관리자 답변을 같은 table에 담고 {@code authorType}으로
 * 구분한다(docs/28_MEMBER_INQUIRY_DESIGN.md 4.3).
 *
 * <p>본문은 평문이다. 비공개 1:1이지만 암호화하면 관리자 키워드 검색이 불가능해지고 이 저장소가
 * 의존하는 {@code char_length} CHECK 제약도 걸 수 없다(docs/28 3.1).
 *
 * <p>수정·삭제 메서드를 두지 않는다. 문의 이력은 고쳐 쓰지 않고, 보관 기간 경과 시에만
 * {@link #anonymize(String)}으로 본문을 덮는다.
 */
@Entity
@Table(name = "inquiry_messages")
public class InquiryMessage {

    public static final int BODY_MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inquiry_id", nullable = false)
    private Long inquiryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 10)
    private InquiryMessageAuthorType authorType;

    @Column(name = "author_member_id", nullable = false)
    private Long authorMemberId;

    @Column(nullable = false, length = BODY_MAX_LENGTH)
    private String body;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected InquiryMessage() {
    }

    /**
     * @param body 호출부가 이미 trim·길이 검증을 마친 본문
     */
    public static InquiryMessage create(
            Long inquiryId,
            InquiryMessageAuthorType authorType,
            Long authorMemberId,
            String body
    ) {
        String normalizedBody = Objects.requireNonNull(body, "body").trim();
        if (normalizedBody.isEmpty() || normalizedBody.length() > BODY_MAX_LENGTH) {
            throw new IllegalArgumentException("body는 1~" + BODY_MAX_LENGTH + "자여야 합니다.");
        }
        InquiryMessage message = new InquiryMessage();
        message.inquiryId = Objects.requireNonNull(inquiryId, "inquiryId");
        message.authorType = Objects.requireNonNull(authorType, "authorType");
        message.authorMemberId = Objects.requireNonNull(authorMemberId, "authorMemberId");
        message.body = normalizedBody;
        return message;
    }

    /** 보관 기간 경과 처리. 본문을 고정 문구로 덮는다(docs/28 5.6). */
    public void anonymize(String placeholderBody) {
        body = placeholderBody;
    }

    @PrePersist
    void prePersist() {
        createdAt = SeoulDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getInquiryId() {
        return inquiryId;
    }

    public InquiryMessageAuthorType getAuthorType() {
        return authorType;
    }

    public Long getAuthorMemberId() {
        return authorMemberId;
    }

    public String getBody() {
        return body;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
