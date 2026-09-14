package com.survey.meetorsolo.domain.content.bookmark.repository;

import com.survey.meetorsolo.domain.content.bookmark.entity.ContentBookmark;
import com.survey.meetorsolo.domain.content.comment.entity.ContentCommentStatus;
import com.survey.meetorsolo.domain.content.support.ContentTargetCountRow;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentBookmarkRepository extends JpaRepository<ContentBookmark, Long> {

    boolean existsByMemberIdAndFestivalId(long memberId, long festivalId);

    boolean existsByMemberIdAndTourPlaceId(long memberId, long tourPlaceId);

    /**
     * 목록 한 페이지 안에서 <b>내가 찜한 대상 id</b>만 골라낸다. 목록 카드의 하트를 채울지
     * 판단하는 값이며, 항목 수와 무관하게 쿼리 1건이라 N+1이 생기지 않는다
     * ({@code ContentCommentLikeRepository.findLikedCommentIds}와 같은 방식).
     */
    @Query("""
            select bookmark.festivalId
            from ContentBookmark bookmark
            where bookmark.memberId = :memberId
              and bookmark.festivalId in :festivalIds
            """)
    List<Long> findBookmarkedFestivalIds(
            @Param("memberId") long memberId,
            @Param("festivalIds") Collection<Long> festivalIds
    );

    @Query("""
            select bookmark.tourPlaceId
            from ContentBookmark bookmark
            where bookmark.memberId = :memberId
              and bookmark.tourPlaceId in :tourPlaceIds
            """)
    List<Long> findBookmarkedTourPlaceIds(
            @Param("memberId") long memberId,
            @Param("tourPlaceIds") Collection<Long> tourPlaceIds
    );

    /**
     * 대상별 찜 수. 목록 조회는 정렬에 필요해 native query 안에서 직접 집계하지만, 반경 검색처럼
     * 정렬 기준이 거리인 조회는 결과가 정해진 뒤 이 쿼리로 한 번에 모은다.
     */
    @Query("""
            select new com.survey.meetorsolo.domain.content.support.ContentTargetCountRow(
                bookmark.festivalId, count(bookmark.id))
            from ContentBookmark bookmark
            where bookmark.festivalId in :festivalIds
            group by bookmark.festivalId
            """)
    List<ContentTargetCountRow> countByFestivalIds(@Param("festivalIds") Collection<Long> festivalIds);

    @Query("""
            select new com.survey.meetorsolo.domain.content.support.ContentTargetCountRow(
                bookmark.tourPlaceId, count(bookmark.id))
            from ContentBookmark bookmark
            where bookmark.tourPlaceId in :tourPlaceIds
            group by bookmark.tourPlaceId
            """)
    List<ContentTargetCountRow> countByTourPlaceIds(@Param("tourPlaceIds") Collection<Long> tourPlaceIds);

    /**
     * 찜 등록. 동시 요청 2건이 들어와도 partial unique index가 1건만 남기고 나머지는
     * {@code ON CONFLICT DO NOTHING}으로 흡수되어 예외 없이 0을 반환한다
     * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 5.1).
     *
     * <p>충돌 대상을 특정하지 않는 {@code ON CONFLICT DO NOTHING}이므로
     * {@code uq_content_bookmarks_member_festival}과 {@code uq_content_bookmarks_member_place}
     * 두 partial unique index를 모두 커버한다.
     *
     * @return 실제로 삽입된 행 수(0 또는 1)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into content_bookmarks (member_id, festival_id, tour_place_id, created_at)
            values (:memberId, :festivalId, :tourPlaceId, :createdAt)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIgnoringConflict(
            @Param("memberId") long memberId,
            @Param("festivalId") Long festivalId,
            @Param("tourPlaceId") Long tourPlaceId,
            @Param("createdAt") OffsetDateTime createdAt
    );

    /** 찜 해제는 물리 삭제다. 대상이 없어도 0을 반환하고 호출부는 그대로 성공 처리한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from content_bookmarks
            where member_id = :memberId and festival_id = :festivalId
            """, nativeQuery = true)
    int deleteFestivalBookmark(
            @Param("memberId") long memberId,
            @Param("festivalId") long festivalId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from content_bookmarks
            where member_id = :memberId and tour_place_id = :tourPlaceId
            """, nativeQuery = true)
    int deleteTourPlaceBookmark(
            @Param("memberId") long memberId,
            @Param("tourPlaceId") long tourPlaceId
    );

    /**
     * 내 찜 목록(축제 탭). {@code HIDDEN} 축제는 제외하고, {@code INACTIVE}·종료 축제는 남긴다 —
     * 화면이 배지로 표시한다(docs/27 7.3).
     */
    @Query(value = """
            select new com.survey.meetorsolo.domain.content.bookmark.repository.BookmarkedFestivalRow(
                festival.id, festival.contentId, festival.title, festival.address,
                festival.areaCode, festival.sigunguCode, festival.eventStartDate,
                festival.eventEndDate, festival.status, festival.mapX, festival.mapY,
                (select count(otherBookmark.id) from ContentBookmark otherBookmark
                  where otherBookmark.festivalId = festival.id),
                (select count(comment.id) from ContentComment comment
                  where comment.festivalId = festival.id and comment.status = :visibleCommentStatus),
                bookmark.createdAt)
            from ContentBookmark bookmark, Festival festival
            where festival.id = bookmark.festivalId
              and bookmark.memberId = :memberId
              and festival.status <> :hiddenStatus
            order by bookmark.createdAt desc, bookmark.id desc
            """,
            countQuery = """
            select count(bookmark.id)
            from ContentBookmark bookmark, Festival festival
            where festival.id = bookmark.festivalId
              and bookmark.memberId = :memberId
              and festival.status <> :hiddenStatus
            """)
    Page<BookmarkedFestivalRow> findBookmarkedFestivals(
            @Param("memberId") long memberId,
            @Param("hiddenStatus") FestivalStatus hiddenStatus,
            @Param("visibleCommentStatus") ContentCommentStatus visibleCommentStatus,
            Pageable pageable
    );

    /** 내 찜 목록(관광지 탭). {@code HIDDEN} 관광지만 제외한다. */
    @Query(value = """
            select new com.survey.meetorsolo.domain.content.bookmark.repository.BookmarkedTourPlaceRow(
                place.id, place.contentId, place.contentTypeId, place.title, place.address,
                place.status, place.imageUrl,
                (select count(otherBookmark.id) from ContentBookmark otherBookmark
                  where otherBookmark.tourPlaceId = place.id),
                (select count(comment.id) from ContentComment comment
                  where comment.tourPlaceId = place.id and comment.status = :visibleCommentStatus),
                bookmark.createdAt)
            from ContentBookmark bookmark, TourPlace place
            where place.id = bookmark.tourPlaceId
              and bookmark.memberId = :memberId
              and place.status <> :hiddenStatus
            order by bookmark.createdAt desc, bookmark.id desc
            """,
            countQuery = """
            select count(bookmark.id)
            from ContentBookmark bookmark, TourPlace place
            where place.id = bookmark.tourPlaceId
              and bookmark.memberId = :memberId
              and place.status <> :hiddenStatus
            """)
    Page<BookmarkedTourPlaceRow> findBookmarkedTourPlaces(
            @Param("memberId") long memberId,
            @Param("hiddenStatus") TourPlaceStatus hiddenStatus,
            @Param("visibleCommentStatus") ContentCommentStatus visibleCommentStatus,
            Pageable pageable
    );

    default Page<BookmarkedFestivalRow> findBookmarkedFestivals(long memberId, Pageable pageable) {
        return findBookmarkedFestivals(
                memberId, FestivalStatus.HIDDEN, ContentCommentStatus.VISIBLE, pageable);
    }

    default Page<BookmarkedTourPlaceRow> findBookmarkedTourPlaces(long memberId, Pageable pageable) {
        return findBookmarkedTourPlaces(
                memberId, TourPlaceStatus.HIDDEN, ContentCommentStatus.VISIBLE, pageable);
    }

    /** 탈퇴 시 개인 데이터인 찜은 물리 삭제한다(docs/27 5.7). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from content_bookmarks where member_id = :memberId", nativeQuery = true)
    int deleteAllByMemberId(@Param("memberId") long memberId);
}
