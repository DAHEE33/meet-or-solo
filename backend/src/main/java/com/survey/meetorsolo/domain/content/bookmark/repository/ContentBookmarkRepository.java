package com.survey.meetorsolo.domain.content.bookmark.repository;

import com.survey.meetorsolo.domain.content.bookmark.entity.ContentBookmark;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import java.time.OffsetDateTime;
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
            Pageable pageable
    );

    /** 내 찜 목록(관광지 탭). {@code HIDDEN} 관광지만 제외한다. */
    @Query(value = """
            select new com.survey.meetorsolo.domain.content.bookmark.repository.BookmarkedTourPlaceRow(
                place.id, place.contentId, place.contentTypeId, place.title, place.address,
                place.status, place.imageUrl, bookmark.createdAt)
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
            Pageable pageable
    );

    default Page<BookmarkedFestivalRow> findBookmarkedFestivals(long memberId, Pageable pageable) {
        return findBookmarkedFestivals(memberId, FestivalStatus.HIDDEN, pageable);
    }

    default Page<BookmarkedTourPlaceRow> findBookmarkedTourPlaces(long memberId, Pageable pageable) {
        return findBookmarkedTourPlaces(memberId, TourPlaceStatus.HIDDEN, pageable);
    }

    /** 탈퇴 시 개인 데이터인 찜은 물리 삭제한다(docs/27 5.7). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from content_bookmarks where member_id = :memberId", nativeQuery = true)
    int deleteAllByMemberId(@Param("memberId") long memberId);
}
