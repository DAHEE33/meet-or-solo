package com.survey.meetorsolo.domain.content.bookmark.service;

import com.survey.meetorsolo.domain.content.bookmark.dto.BookmarkedContentItemResponse;
import com.survey.meetorsolo.domain.content.bookmark.dto.BookmarkedContentListResponse;
import com.survey.meetorsolo.domain.content.bookmark.dto.ContentBookmarkToggleResponse;
import com.survey.meetorsolo.domain.content.bookmark.repository.BookmarkedFestivalRow;
import com.survey.meetorsolo.domain.content.bookmark.repository.BookmarkedTourPlaceRow;
import com.survey.meetorsolo.domain.content.bookmark.repository.ContentBookmarkRepository;
import com.survey.meetorsolo.domain.content.support.ContentTarget;
import com.survey.meetorsolo.domain.content.support.ContentTargetReader;
import com.survey.meetorsolo.domain.content.support.ContentTargetType;
import com.survey.meetorsolo.domain.festival.dto.FestivalListItemResponse;
import com.survey.meetorsolo.domain.festival.entity.FestivalImage;
import com.survey.meetorsolo.domain.festival.repository.FestivalImageRepository;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListItemResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 찜 등록·해제와 내 찜 목록 조회.
 *
 * <p>정지·영구제한 회원 차단은 {@code MemberAccessInterceptor}가 {@code /api/**}에서 이미
 * 처리하므로 여기서 다시 검사하지 않는다(docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 5.1).
 */
@Service
public class ContentBookmarkService {

    private final ContentBookmarkRepository bookmarks;
    private final ContentTargetReader targets;
    private final FestivalImageRepository festivalImages;
    private final Clock clock;

    public ContentBookmarkService(
            ContentBookmarkRepository bookmarks,
            ContentTargetReader targets,
            FestivalImageRepository festivalImages,
            Clock clock
    ) {
        this.bookmarks = bookmarks;
        this.targets = targets;
        this.festivalImages = festivalImages;
        this.clock = clock;
    }

    /**
     * 찜 토글. 요청한 상태를 그대로 돌려주는 멱등 연산이다.
     *
     * <p>동시에 {@code bookmarked = true} 2건이 들어와도 partial unique index가 1건만 남기고
     * 두 요청 모두 성공한다 — {@code ON CONFLICT DO NOTHING}이 충돌을 예외 없이 흡수한다.
     */
    @Transactional
    public ContentBookmarkToggleResponse toggle(long memberId, ContentTarget target, boolean bookmarked) {
        targets.requireVisible(target);
        if (bookmarked) {
            bookmarks.insertIgnoringConflict(
                    memberId,
                    target.festivalId(),
                    target.tourPlaceId(),
                    OffsetDateTime.now(clock)
            );
        } else if (target.isFestival()) {
            bookmarks.deleteFestivalBookmark(memberId, target.id());
        } else {
            bookmarks.deleteTourPlaceBookmark(memberId, target.id());
        }
        return new ContentBookmarkToggleResponse(bookmarked);
    }

    @Transactional(readOnly = true)
    public boolean isBookmarked(Long memberId, ContentTarget target) {
        if (memberId == null) {
            return false;
        }
        return target.isFestival()
                ? bookmarks.existsByMemberIdAndFestivalId(memberId, target.id())
                : bookmarks.existsByMemberIdAndTourPlaceId(memberId, target.id());
    }

    /**
     * 내 찜 목록. {@code HIDDEN} 대상은 repository 쿼리에서 제외되고, {@code INACTIVE}·종료
     * 대상은 남아 화면이 배지로 표시한다(docs/27 7.3).
     */
    @Transactional(readOnly = true)
    public BookmarkedContentListResponse getMyBookmarks(
            long memberId,
            ContentTargetType type,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        if (type == ContentTargetType.FESTIVAL) {
            Page<BookmarkedFestivalRow> rows = bookmarks.findBookmarkedFestivals(memberId, pageable);
            Map<Long, FestivalImage> images = representativeFestivalImages(rows.getContent());
            return toListResponse(rows.map(row -> toItem(row, images.get(row.id()))));
        }
        return toListResponse(bookmarks.findBookmarkedTourPlaces(memberId, pageable).map(this::toItem));
    }

    private BookmarkedContentListResponse toListResponse(Page<BookmarkedContentItemResponse> page) {
        return new BookmarkedContentListResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }

    /**
     * 축제 대표 이미지를 한 번에 조회한다({@code festivalId -> 이미지}).
     * {@code FestivalQueryService.representativeImages}와 같은 방식으로 {@code displayOrder}가
     * 가장 작은 이미지를 대표로 쓴다 — repository 쿼리가 이미 그 순서로 정렬해 내려주므로 처음
     * 만난 이미지가 대표다.
     */
    private Map<Long, FestivalImage> representativeFestivalImages(List<BookmarkedFestivalRow> rows) {
        List<Long> festivalIds = rows.stream().map(BookmarkedFestivalRow::id).toList();
        if (festivalIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, FestivalImage> imagesByFestivalId = new LinkedHashMap<>();
        for (FestivalImage image : festivalImages.findAllByFestivalIdIn(festivalIds)) {
            imagesByFestivalId.putIfAbsent(image.getFestivalId(), image);
        }
        return imagesByFestivalId;
    }

    /**
     * 찜 목록 항목으로 조립한다. 축제 부분은 목록 화면과 완전히 같은
     * {@code FestivalListItemResponse}라 화면이 카드 컴포넌트를 그대로 재사용한다.
     */
    private BookmarkedContentItemResponse toItem(BookmarkedFestivalRow row, FestivalImage image) {
        return BookmarkedContentItemResponse.ofFestival(
                row.bookmarkedAt(),
                new FestivalListItemResponse(
                        row.id(),
                        row.contentId(),
                        row.title(),
                        row.address(),
                        row.regionCode(),
                        row.sigunguCode(),
                        row.eventStartDate(),
                        row.eventEndDate(),
                        row.status(),
                        image == null ? null : image.getOriginImageUrl(),
                        image == null ? null : image.getThumbnailUrl(),
                        row.mapX(),
                        row.mapY()
                )
        );
    }

    private BookmarkedContentItemResponse toItem(BookmarkedTourPlaceRow row) {
        return BookmarkedContentItemResponse.ofTourPlace(
                row.bookmarkedAt(),
                new TourPlaceListItemResponse(
                        row.id(),
                        row.contentId(),
                        row.contentTypeId(),
                        row.title(),
                        row.address(),
                        row.status(),
                        row.imageUrl()
                )
        );
    }

    /** 탈퇴 시 개인 데이터인 찜은 물리 삭제한다(docs/27 5.7). */
    @Transactional
    public int deleteAllOnWithdrawal(long memberId) {
        return bookmarks.deleteAllByMemberId(memberId);
    }
}
