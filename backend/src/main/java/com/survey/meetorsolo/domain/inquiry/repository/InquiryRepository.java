package com.survey.meetorsolo.domain.inquiry.repository;

import com.survey.meetorsolo.domain.inquiry.entity.Inquiry;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 상태값은 JPQL 리터럴이 아니라 {@code :param}으로 넘긴다 — {@code ContentCommentRepository} 등
 * 기존 repository와 같은 방식이다.
 */
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    /**
     * 상태를 바꾸는 모든 경로가 이 메서드로 헤더를 먼저 잠근다.
     *
     * <p>잠그지 않으면 관리자 답변({@code status = ANSWERED})과 종결({@code status = CLOSED},
     * {@code closed_at = now})이 겹칠 때 {@code chk_inquiries_closed_at}을 위반하는 조합
     * ({@code ANSWERED}인데 {@code closed_at}이 남은 상태)이 만들어질 수 있다(docs/28 5.9).
     */
    @Query(value = "SELECT * FROM inquiries WHERE id = :inquiryId FOR UPDATE", nativeQuery = true)
    Optional<Inquiry> findByIdForUpdate(@Param("inquiryId") long inquiryId);

    /** 내 문의 목록. {@code idx_inquiries_member_created_at}을 탄다(docs/28 5.2). */
    @Query(value = """
            select inquiry
            from Inquiry inquiry
            where inquiry.memberId = :memberId
            order by inquiry.createdAt desc, inquiry.id desc
            """,
            countQuery = """
            select count(inquiry.id)
            from Inquiry inquiry
            where inquiry.memberId = :memberId
            """)
    Page<Inquiry> findPageByMemberId(@Param("memberId") long memberId, Pageable pageable);

    /**
     * 미답변 문의 수. 등록 제한(3건)이 쓴다.
     * {@code idx_inquiries_open} partial index를 탄다(docs/28 5.1).
     */
    @Query("""
            select count(inquiry.id)
            from Inquiry inquiry
            where inquiry.memberId = :memberId
              and inquiry.status in :openStatuses
            """)
    long countOpenByMemberId(
            @Param("memberId") long memberId,
            @Param("openStatuses") List<InquiryStatus> openStatuses
    );

    /**
     * 사용자가 아직 못 본 답변이 있는 문의 수. {@code MyPage} badge가 쓴다.
     *
     * <p>판정 식은 {@code Inquiry.hasUnreadAnswer()}와 같아야 한다. 두 곳이 어긋나면 badge 숫자와
     * 목록 표시가 달라진다.
     */
    @Query("""
            select count(inquiry.id)
            from Inquiry inquiry
            where inquiry.memberId = :memberId
              and inquiry.lastAnsweredAt is not null
              and (inquiry.memberReadAt is null or inquiry.memberReadAt < inquiry.lastAnsweredAt)
            """)
    long countUnreadAnswersByMemberId(@Param("memberId") long memberId);

    /**
     * 보관 기간이 지난 종결 문의. {@code idx_inquiries_retention} partial index를 탄다.
     * 오래 방치된 것부터 처리하도록 {@code closedAt} 오름차순이다(docs/28 5.6).
     */
    @Query("""
            select inquiry
            from Inquiry inquiry
            where inquiry.status = :closedStatus
              and inquiry.closedAt < :threshold
              and inquiry.anonymizedAt is null
            order by inquiry.closedAt asc, inquiry.id asc
            """)
    List<Inquiry> findAnonymizationTargets(
            @Param("closedStatus") InquiryStatus closedStatus,
            @Param("threshold") OffsetDateTime threshold,
            Pageable pageable
    );

    // --- 상태 상수를 채워주는 편의 메서드 ---------------------------------------------------
    // 위 쿼리들이 상태값을 파라미터로 받는 것은 JPQL 안에 enum 리터럴을 쓰지 않기 위한 것이고,
    // 호출부가 매번 상수를 넘길 이유는 없어 여기서 감싼다.

    default long countOpenByMemberId(long memberId) {
        return countOpenByMemberId(
                memberId, List.of(InquiryStatus.RECEIVED, InquiryStatus.IN_PROGRESS));
    }

    default List<Inquiry> findAnonymizationTargets(OffsetDateTime threshold, int batchSize) {
        return findAnonymizationTargets(
                InquiryStatus.CLOSED, threshold, PageRequest.of(0, batchSize));
    }
}
