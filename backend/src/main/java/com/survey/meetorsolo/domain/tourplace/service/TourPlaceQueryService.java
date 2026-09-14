package com.survey.meetorsolo.domain.tourplace.service;

import com.survey.meetorsolo.domain.content.engagement.service.ContentEngagementSummaryReader;
import com.survey.meetorsolo.domain.content.support.ContentTargetType;
import com.survey.meetorsolo.domain.festival.dto.NearbyFestivalResponse;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalImage;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalImageRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceDetailResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListItemResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListSort;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlace;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import com.survey.meetorsolo.domain.tourplace.repository.TourPlaceRepository;
import com.survey.meetorsolo.domain.tourplace.repository.TourPlaceRepository.TourPlaceListProjection;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.geo.GeoDistanceCalculator;
import com.survey.meetorsolo.global.region.RegionNameResolver;
import com.survey.meetorsolo.global.region.RegionOptionResponse;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TourPlaceQueryService {

    private final TourPlaceRepository tourPlaceRepository;
    private final FestivalRepository festivalRepository;
    private final FestivalImageRepository festivalImageRepository;
    private final ContentEngagementSummaryReader engagementSummaries;

    public TourPlaceQueryService(
            TourPlaceRepository tourPlaceRepository,
            FestivalRepository festivalRepository,
            FestivalImageRepository festivalImageRepository,
            ContentEngagementSummaryReader engagementSummaries
    ) {
        this.tourPlaceRepository = tourPlaceRepository;
        this.festivalRepository = festivalRepository;
        this.festivalImageRepository = festivalImageRepository;
        this.engagementSummaries = engagementSummaries;
    }

    @Transactional(readOnly = true)
    public TourPlaceListResponse getVisiblePlaces(
            int page,
            int size,
            String contentTypeId,
            String keyword,
            String sigunguCode,
            TourPlaceListSort sort,
            Long viewerMemberId
    ) {
        TourPlaceListSort effectiveSort = sort == null ? TourPlaceListSort.TITLE_ASC : sort;
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<TourPlaceListProjection> placePage = tourPlaceRepository.findVisiblePlaces(
                TourPlaceStatus.ACTIVE.name(),
                normalizeOrNull(contentTypeId),
                normalizeOrNull(sigunguCode),
                normalizeKeyword(keyword),
                effectiveSort.name(),
                pageRequest
        );

        // 찜 수·댓글 수는 정렬에 필요해 쿼리가 이미 집계했고, "내가 찜했는지"만 한 번 더 모은다.
        List<Long> placeIds = placePage.getContent().stream()
                .map(TourPlaceListProjection::getId)
                .toList();
        Set<Long> bookmarkedIds = engagementSummaries.bookmarkedIds(
                ContentTargetType.TOUR_PLACE, placeIds, viewerMemberId
        );

        return new TourPlaceListResponse(
                placePage.getContent().stream()
                        .map(place -> toListItem(place, bookmarkedIds.contains(place.getId())))
                        .toList(),
                placePage.getNumber(),
                placePage.getSize(),
                placePage.getTotalElements(),
                placePage.getTotalPages(),
                placePage.hasNext(),
                viewerMemberId != null
        );
    }

    private static TourPlaceListItemResponse toListItem(
            TourPlaceListProjection place,
            boolean bookmarkedByMe
    ) {
        return new TourPlaceListItemResponse(
                place.getId(),
                place.getContentId(),
                place.getContentTypeId(),
                place.getTitle(),
                place.getAddress(),
                TourPlaceStatus.valueOf(place.getStatus()),
                place.getImageUrl(),
                place.getBookmarkCount(),
                place.getCommentCount(),
                bookmarkedByMe
        );
    }

    @Transactional(readOnly = true)
    public TourPlaceDetailResponse getTourPlaceDetail(Long id) {
        TourPlace place = tourPlaceRepository.findById(id)
                .filter(found -> found.getStatus() != TourPlaceStatus.HIDDEN)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "관광지를 찾을 수 없습니다."));

        return new TourPlaceDetailResponse(
                place.getId(),
                place.getContentId(),
                place.getContentTypeId(),
                place.getTitle(),
                place.getAddress(),
                place.getTel(),
                place.getMapX(),
                place.getMapY(),
                place.getStatus(),
                place.getImageUrl()
        );
    }

    /**
     * 관광지 상세의 "이 장소 주변에서 열리는 축제".
     *
     * <p>카드에서 바로 찜을 토글하므로 찜 수·댓글 수와 내 찜 여부를 함께 내려준다. 집계는
     * <b>반경·정렬·개수 제한을 모두 끝낸 뒤</b> 최종 목록에 대해서만 수행한다 — bounding box
     * 후보는 반경 밖 축제까지 포함하므로 먼저 집계하면 버려질 행까지 세게 된다.
     *
     * @param viewerMemberId 비로그인이면 {@code null}. 이 조회는 공개라 예외를 던지지 않는다
     */
    @Transactional(readOnly = true)
    public List<NearbyFestivalResponse> getNearbyFestivals(
            Long tourPlaceId,
            int radiusMeters,
            int limit,
            Long viewerMemberId
    ) {
        TourPlace place = tourPlaceRepository.findById(tourPlaceId)
                .filter(found -> found.getStatus() != TourPlaceStatus.HIDDEN)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "관광지를 찾을 수 없습니다."));
        if (place.getMapX() == null || place.getMapY() == null) {
            return List.of();
        }

        LocalDate today = LocalDate.now(SeoulDateTime.ZONE_ID);
        GeoDistanceCalculator.BoundingBox box = GeoDistanceCalculator.boundingBox(
                place.getMapY(), place.getMapX(), radiusMeters
        );
        List<Festival> candidates = festivalRepository.findAllVisibleWithinBoundingBox(
                FestivalStatus.ACTIVE,
                today,
                box.minLongitude(), box.maxLongitude(),
                box.minLatitude(), box.maxLatitude()
        );

        record Nearby(Festival festival, long distanceMeters) {
        }
        List<Nearby> nearest = candidates.stream()
                .map(festival -> new Nearby(festival, GeoDistanceCalculator.metersBetween(
                        place.getMapY(), place.getMapX(), festival.getMapY(), festival.getMapX()
                )))
                .filter(nearby -> nearby.distanceMeters() <= radiusMeters)
                .sorted(Comparator.comparingLong(Nearby::distanceMeters))
                .limit(limit)
                .toList();

        List<Long> festivalIds = nearest.stream().map(nearby -> nearby.festival().getId()).toList();
        var thumbnailsByFestivalId = festivalImageRepository.findAllByFestivalIdIn(festivalIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        FestivalImage::getFestivalId,
                        FestivalImage::getThumbnailUrl,
                        (first, second) -> first
                ));
        Map<Long, ContentEngagementSummaryReader.Summary> summaries = engagementSummaries.summarize(
                ContentTargetType.FESTIVAL, festivalIds, viewerMemberId
        );

        return nearest.stream()
                .map(nearby -> toNearbyResponse(
                        nearby.festival(),
                        nearby.distanceMeters(),
                        thumbnailsByFestivalId.get(nearby.festival().getId()),
                        summaries.getOrDefault(
                                nearby.festival().getId(),
                                ContentEngagementSummaryReader.Summary.EMPTY
                        )
                ))
                .toList();
    }

    private NearbyFestivalResponse toNearbyResponse(
            Festival festival,
            long distanceMeters,
            String thumbnailUrl,
            ContentEngagementSummaryReader.Summary summary
    ) {
        return new NearbyFestivalResponse(
                festival.getId(),
                festival.getTitle(),
                festival.getAddress(),
                festival.getEventStartDate(),
                festival.getEventEndDate(),
                festival.getStatus(),
                thumbnailUrl,
                distanceMeters,
                summary.bookmarkCount(),
                summary.commentCount(),
                summary.bookmarkedByMe()
        );
    }

    private String normalizeOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 지역 선택 UI용 시군구 목록. 카테고리를 함께 넘기면 그 카테고리에 실제로 장소가 있는
     * 지역만 반환하므로, 선택했을 때 빈 결과가 나오는 조합이 노출되지 않는다.
     */
    @Transactional(readOnly = true)
    public List<RegionOptionResponse> getTourPlaceRegions(String contentTypeId) {
        return RegionNameResolver.toOptions(
                tourPlaceRepository.aggregateVisibleRegions(
                        TourPlaceStatus.ACTIVE,
                        normalizeOrNull(contentTypeId)
                )
        );
    }

    /**
     * PostgreSQL이 {@code lower(concat('%', :keyword, '%'))}에 바인딩되는 null 파라미터의
     * 타입을 추론하지 못해 오류가 나므로(bytea로 오판), null 대신 빈 문자열을 사용해 항상
     * LIKE 패턴을 적용한다. 빈 문자열이면 {@code '%%'}가 되어 모든 제목과 매칭된다.
     */
    private String normalizeKeyword(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }
}
