package com.survey.meetorsolo.domain.content.comment.repository;

import com.survey.meetorsolo.domain.content.comment.entity.ContentComment;
import com.survey.meetorsolo.domain.content.comment.entity.ContentCommentStatus;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 상태값은 JPQL 리터럴이 아니라 {@code :param}으로 넘긴다 — {@code TourPlaceRepository} 등
 * 기존 repository와 같은 방식이다.
 *
 * <p><b>탈퇴 회원 닉네임 치환이 없는 이유</b>: 다른 조회 경로는 탈퇴 회원의 닉네임 컬럼이
 * {@code NULL}이라 {@code status = 'WITHDRAWN'}일 때 표시 문구를 SQL에서 만들어 낸다
 * ({@code docs/19} 4.4). 댓글 목록은 그럴 필요가 없다 — 탈퇴가
 * {@code ContentCommentService.softDeleteAllOnWithdrawal}로 작성 댓글을 {@code VISIBLE} →
 * {@code DELETED}로 내리고, 아래 목록은 {@code comment.status = :status}로 {@code VISIBLE}만
 * 조회하므로 탈퇴 회원의 댓글이 애초에 결과에 들어오지 않는다.
 *
 * <p>탈퇴 회원 댓글을 계속 보이게 정책이 바뀌면 <b>여기에도 치환을 넣어야 한다.</b> 넣지 않으면
 * 닉네임이 {@code null}로 내려가고, 프론트가 {@code nickname.slice(0, 1)}로 첫 글자를 뽑으므로
 * ({@code ContentCommentItem.tsx}) 빈 칸이 아니라 렌더링 자체가 죽는다.
 */
public interface ContentCommentRepository extends JpaRepository<ContentComment, Long> {

    /**
     * 축제 댓글 목록. 정렬 키가 {@code id desc}인 이유는 동시 삽입 tie-break가 필요 없고
     * {@code idx_content_comments_festival_visible} partial index가 그대로 적중하기 때문이다
     * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 3.3).
     *
     * <p>축제용과 관광지용을 한 메서드로 합치지 않는다 — {@code :festivalId is null or ...} 형태로
     * 합치면 위 partial index를 타지 못한다.
     */
    @Query(value = """
            select new com.survey.meetorsolo.domain.content.comment.repository.ContentCommentRow(
                comment.id, comment.memberId, member.nickname, comment.body,
                comment.likeCount, comment.createdAt)
            from ContentComment comment, Member member
            where member.id = comment.memberId
              and comment.festivalId = :festivalId
              and comment.status = :status
            order by comment.id desc
            """,
            countQuery = """
            select count(comment.id)
            from ContentComment comment
            where comment.festivalId = :festivalId
              and comment.status = :status
            """)
    Page<ContentCommentRow> findVisibleByFestivalId(
            @Param("festivalId") long festivalId,
            @Param("status") ContentCommentStatus status,
            Pageable pageable
    );

    @Query(value = """
            select new com.survey.meetorsolo.domain.content.comment.repository.ContentCommentRow(
                comment.id, comment.memberId, member.nickname, comment.body,
                comment.likeCount, comment.createdAt)
            from ContentComment comment, Member member
            where member.id = comment.memberId
              and comment.tourPlaceId = :tourPlaceId
              and comment.status = :status
            order by comment.id desc
            """,
            countQuery = """
            select count(comment.id)
            from ContentComment comment
            where comment.tourPlaceId = :tourPlaceId
              and comment.status = :status
            """)
    Page<ContentCommentRow> findVisibleByTourPlaceId(
            @Param("tourPlaceId") long tourPlaceId,
            @Param("status") ContentCommentStatus status,
            Pageable pageable
    );

    long countByFestivalIdAndStatus(long festivalId, ContentCommentStatus status);

    long countByTourPlaceIdAndStatus(long tourPlaceId, ContentCommentStatus status);

    /**
     * 도배 완화 5초 규칙용 — 같은 회원이 가장 최근에 남긴 댓글 시각.
     * {@code idx_content_comments_member_created_at}을 탄다(docs/27 5.2).
     */
    @Query("""
            select max(comment.createdAt)
            from ContentComment comment
            where comment.memberId = :memberId
            """)
    Optional<OffsetDateTime> findLatestCreatedAtByMemberId(@Param("memberId") long memberId);

    /**
     * 작성자 본인 삭제. affected가 0이면 이미 삭제된 것으로 보고 호출부가 그대로 {@code 204}를
     * 반환한다(멱등, docs/27 5.3).
     *
     * @return 실제로 전환된 행 수(0 또는 1)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ContentComment comment
            set comment.status = :deletedStatus,
                comment.deletedAt = :deletedAt,
                comment.updatedAt = :deletedAt
            where comment.id = :commentId
              and comment.memberId = :memberId
              and comment.status = :visibleStatus
            """)
    int softDeleteByAuthor(
            @Param("commentId") long commentId,
            @Param("memberId") long memberId,
            @Param("deletedAt") OffsetDateTime deletedAt,
            @Param("deletedStatus") ContentCommentStatus deletedStatus,
            @Param("visibleStatus") ContentCommentStatus visibleStatus
    );

    /** 관리자 숨김. {@code VISIBLE}만 숨긴다 — 작성자가 삭제한 {@code DELETED}는 건드리지 않는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ContentComment comment
            set comment.status = :hiddenStatus,
                comment.deletedAt = :hiddenAt,
                comment.updatedAt = :hiddenAt
            where comment.id = :commentId
              and comment.status = :visibleStatus
            """)
    int hideByAdmin(
            @Param("commentId") long commentId,
            @Param("hiddenAt") OffsetDateTime hiddenAt,
            @Param("hiddenStatus") ContentCommentStatus hiddenStatus,
            @Param("visibleStatus") ContentCommentStatus visibleStatus
    );

    /**
     * 관리자 재공개. {@code HIDDEN}만 되돌린다 — 작성자가 삭제한 {@code DELETED}는 관리자가
     * 되살리지 않는다(docs/27 5.6).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ContentComment comment
            set comment.status = :visibleStatus,
                comment.deletedAt = null,
                comment.updatedAt = :updatedAt
            where comment.id = :commentId
              and comment.status = :hiddenStatus
            """)
    int showByAdmin(
            @Param("commentId") long commentId,
            @Param("updatedAt") OffsetDateTime updatedAt,
            @Param("visibleStatus") ContentCommentStatus visibleStatus,
            @Param("hiddenStatus") ContentCommentStatus hiddenStatus
    );

    /**
     * 탈퇴 시 해당 회원의 공개 댓글을 일괄 숨긴다(docs/27 5.7).
     * 탈퇴 시 닉네임·이미지를 익명화하는 기존 정책과 일관되게, 공개 콘텐츠도 남기지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ContentComment comment
            set comment.status = :deletedStatus,
                comment.deletedAt = :deletedAt,
                comment.updatedAt = :deletedAt
            where comment.memberId = :memberId
              and comment.status = :visibleStatus
            """)
    int softDeleteAllByMemberId(
            @Param("memberId") long memberId,
            @Param("deletedAt") OffsetDateTime deletedAt,
            @Param("deletedStatus") ContentCommentStatus deletedStatus,
            @Param("visibleStatus") ContentCommentStatus visibleStatus
    );

    /**
     * 좋아요 카운터 증가. 호출부는 {@code content_comment_likes} INSERT가 실제로 1행을 넣었을
     * 때만 이 메서드를 부른다 — 그것이 멱등성과 카운터 정합성을 동시에 만족시키는 지점이다
     * (docs/27 2.2).
     *
     * <p>{@code like_count = like_count + 1}은 PostgreSQL row-level lock으로 직렬화되므로
     * lost update가 없다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update content_comments
            set like_count = like_count + 1, updated_at = :updatedAt
            where id = :commentId
            """, nativeQuery = true)
    int increaseLikeCount(
            @Param("commentId") long commentId,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    /**
     * 좋아요 카운터 감소. {@code where like_count > 0} 가드와 DB의
     * {@code chk_content_comments_like_count}가 음수를 이중으로 막는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update content_comments
            set like_count = like_count - 1, updated_at = :updatedAt
            where id = :commentId and like_count > 0
            """, nativeQuery = true)
    int decreaseLikeCount(
            @Param("commentId") long commentId,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    /** 토글 후 실제 카운터를 다시 읽는다. 카운터 update가 persistence context를 비우기 때문이다. */
    @Query("select comment.likeCount from ContentComment comment where comment.id = :commentId")
    Optional<Integer> findLikeCountById(@Param("commentId") long commentId);

    // --- 상태 상수를 채워주는 편의 메서드 ---------------------------------------------------
    // 위 쿼리들이 상태값을 파라미터로 받는 것은 JPQL 안에 enum 리터럴을 쓰지 않기 위한 것이고,
    // 호출부가 매번 상수를 넘길 이유는 없어 여기서 감싼다.

    default Page<ContentCommentRow> findVisibleByFestivalId(long festivalId, Pageable pageable) {
        return findVisibleByFestivalId(festivalId, ContentCommentStatus.VISIBLE, pageable);
    }

    default Page<ContentCommentRow> findVisibleByTourPlaceId(long tourPlaceId, Pageable pageable) {
        return findVisibleByTourPlaceId(tourPlaceId, ContentCommentStatus.VISIBLE, pageable);
    }

    default long countVisibleByFestivalId(long festivalId) {
        return countByFestivalIdAndStatus(festivalId, ContentCommentStatus.VISIBLE);
    }

    default long countVisibleByTourPlaceId(long tourPlaceId) {
        return countByTourPlaceIdAndStatus(tourPlaceId, ContentCommentStatus.VISIBLE);
    }

    default int softDeleteByAuthor(long commentId, long memberId, OffsetDateTime deletedAt) {
        return softDeleteByAuthor(commentId, memberId, deletedAt,
                ContentCommentStatus.DELETED, ContentCommentStatus.VISIBLE);
    }

    default int hideByAdmin(long commentId, OffsetDateTime hiddenAt) {
        return hideByAdmin(commentId, hiddenAt,
                ContentCommentStatus.HIDDEN, ContentCommentStatus.VISIBLE);
    }

    default int showByAdmin(long commentId, OffsetDateTime updatedAt) {
        return showByAdmin(commentId, updatedAt,
                ContentCommentStatus.VISIBLE, ContentCommentStatus.HIDDEN);
    }

    default int softDeleteAllByMemberId(long memberId, OffsetDateTime deletedAt) {
        return softDeleteAllByMemberId(memberId, deletedAt,
                ContentCommentStatus.DELETED, ContentCommentStatus.VISIBLE);
    }
}
