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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 1:1 문의 스레드 1건의 헤더.
 *
 * <p>본문은 {@link InquiryMessage}에 있다. 헤더와 발화를 나눈 이유는
 * docs/28_MEMBER_INQUIRY_DESIGN.md 3.2절이다.
 *
 * <p>{@code lastMessageAt}, {@code lastAnsweredAt}은 목록 정렬과 미확인 badge용 비정규화
 * 값이다. 발화 INSERT와 <b>같은 transaction</b>에서 갱신해야 한다. 어긋나면 badge가 틀린다.
 *
 * <p>미확인 답변 여부는 컬럼으로 저장하지 않는다. {@link #hasUnreadAnswer()}처럼 조회 시점에
 * 계산한다 — boolean 컬럼을 두면 {@code content_comments.like_count}와 같은 카운터 정합성
 * 문제를 새로 만든다(docs/28 4.2).
 */
@Entity
@Table(name = "inquiries")
public class Inquiry {

    public static final int TITLE_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InquiryCategory category;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status = InquiryStatus.RECEIVED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InquiryPriority priority = InquiryPriority.NORMAL;

    @Column(name = "last_message_at", nullable = false)
    private OffsetDateTime lastMessageAt;

    @Column(name = "last_answered_at")
    private OffsetDateTime lastAnsweredAt;

    @Column(name = "member_read_at")
    private OffsetDateTime memberReadAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "anonymized_at")
    private OffsetDateTime anonymizedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Inquiry() {
    }

    /**
     * @param title 호출부가 이미 trim·길이 검증을 마친 제목
     * @param now   등록 시각. {@code lastMessageAt} 초기값으로도 쓴다
     */
    public static Inquiry create(
            Long memberId, InquiryCategory category, String title, OffsetDateTime now) {
        String normalizedTitle = Objects.requireNonNull(title, "title").trim();
        if (normalizedTitle.isEmpty() || normalizedTitle.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("title은 1~" + TITLE_MAX_LENGTH + "자여야 합니다.");
        }
        Inquiry inquiry = new Inquiry();
        inquiry.memberId = Objects.requireNonNull(memberId, "memberId");
        inquiry.category = Objects.requireNonNull(category, "category");
        inquiry.title = normalizedTitle;
        inquiry.status = InquiryStatus.RECEIVED;
        inquiry.priority = InquiryPriority.NORMAL;
        inquiry.lastMessageAt = Objects.requireNonNull(now, "now");
        return inquiry;
    }

    /**
     * 사용자가 발화를 추가했다. 답변 완료였으면 확인 중으로 되돌린다 — 그러지 않으면 관리자
     * 미처리 목록에 다시 뜨지 않아 재질문이 묻힌다(docs/28 5.4).
     */
    public void onUserMessage(OffsetDateTime now) {
        lastMessageAt = now;
        if (status == InquiryStatus.ANSWERED) {
            status = InquiryStatus.IN_PROGRESS;
        }
    }

    /** 관리자가 답변했다. */
    public void onAdminAnswer(OffsetDateTime now) {
        lastMessageAt = now;
        lastAnsweredAt = now;
        status = InquiryStatus.ANSWERED;
    }

    /**
     * 사용자가 스레드를 열었다. 뒤로 가는 갱신은 하지 않아 반복 호출이 멱등하다(docs/28 5.3).
     *
     * @return 실제로 갱신했으면 {@code true}
     */
    public boolean markReadBy(OffsetDateTime now) {
        if (memberReadAt != null && !memberReadAt.isBefore(now)) {
            return false;
        }
        memberReadAt = now;
        return true;
    }

    public void changeStatus(InquiryStatus target, OffsetDateTime now) {
        status = Objects.requireNonNull(target, "target");
        closedAt = target == InquiryStatus.CLOSED ? now : null;
    }

    public void changePriority(InquiryPriority target) {
        priority = Objects.requireNonNull(target, "target");
    }

    /** 보관 기간 경과 처리. 제목을 고정 문구로 덮고 재처리를 막는다(docs/28 5.6). */
    public void anonymize(String placeholderTitle, OffsetDateTime now) {
        title = placeholderTitle;
        anonymizedAt = now;
    }

    /**
     * 사용자가 아직 못 본 답변이 있는지. 관리자 답변 시각과 사용자 열람 시각만으로 판정한다.
     */
    public boolean hasUnreadAnswer() {
        if (lastAnsweredAt == null) {
            return false;
        }
        return memberReadAt == null || memberReadAt.isBefore(lastAnsweredAt);
    }

    public boolean isOwnedBy(long candidateMemberId) {
        return memberId != null && memberId == candidateMemberId;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = SeoulDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (lastMessageAt == null) {
            lastMessageAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = SeoulDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public InquiryCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public InquiryStatus getStatus() {
        return status;
    }

    public InquiryPriority getPriority() {
        return priority;
    }

    public OffsetDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public OffsetDateTime getLastAnsweredAt() {
        return lastAnsweredAt;
    }

    public OffsetDateTime getMemberReadAt() {
        return memberReadAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public OffsetDateTime getAnonymizedAt() {
        return anonymizedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
