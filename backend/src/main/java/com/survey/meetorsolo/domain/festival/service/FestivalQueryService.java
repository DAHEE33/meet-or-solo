package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.content.engagement.service.ContentEngagementSummaryReader;
import com.survey.meetorsolo.domain.content.support.ContentTargetType;
import com.survey.meetorsolo.domain.festival.dto.FestivalDetailInfo;
import com.survey.meetorsolo.domain.festival.dto.FestivalDetailResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalListItemResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalListResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalListSort;
import com.survey.meetorsolo.domain.festival.dto.FestivalProgressFilter;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalImage;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPointStatus;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalImageRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository.FestivalListProjection;
import com.survey.meetorsolo.domain.tourplace.dto.NearbyTourPlaceResponse;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlace;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import com.survey.meetorsolo.domain.tourplace.repository.TourPlaceRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.geo.GeoDistanceCalculator;
import com.survey.meetorsolo.global.region.RegionNameResolver;
import com.survey.meetorsolo.global.region.RegionOptionResponse;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FestivalQueryService {

    private final FestivalRepository festivalRepository;
    private final FestivalImageRepository festivalImageRepository;
    private final TourPlaceRepository tourPlaceRepository;
    private final FestivalDetailInfoService festivalDetailInfoService;
    private final ContentEngagementSummaryReader engagementSummaries;

    public FestivalQueryService(
            FestivalRepository festivalRepository,
            FestivalImageRepository festivalImageRepository,
            TourPlaceRepository tourPlaceRepository,
            FestivalDetailInfoService festivalDetailInfoService,
            ContentEngagementSummaryReader engagementSummaries
    ) {
        this.festivalRepository = festivalRepository;
        this.festivalImageRepository = festivalImageRepository;
        this.tourPlaceRepository = tourPlaceRepository;
        this.festivalDetailInfoService = festivalDetailInfoService;
        this.engagementSummaries = engagementSummaries;
    }

    /**
     * 축제 목록. 모든 필터는 선택이며, 아무것도 넘기지 않으면 기존과 같은 가시성 규칙
     * ({@code ACTIVE} + 종료일이 지나지 않음)이 그대로 적용된다.
     *
     * <p>{@code progress}를 넘긴 호출만 종료된 축제까지 조회한다. 홈 화면과 관광지 상세가 같은
     * API를 쓰고 있어 기본 동작을 바꿀 수 없기 때문이다
     * (docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md).
     */
    @Transactional(readOnly = true)
    public FestivalListResponse getActiveFestivals(
            int page,
            int size,
            String keyword,
            String sigunguCode,
            FestivalListSort sort,
            LocalDate startDate,
            LocalDate endDate,
            FestivalProgressFilter progress,
            boolean matchableOnly,
            Long viewerMemberId
    ) {
        FestivalListSort effectiveSort = sort == null ? FestivalListSort.RECENTLY_ADDED : sort;
        // progress를 명시한 호출만 가시성을 넓힌다. null이면 조건이 아무것도 걸러내지 않도록
        // ALL을 넘기되 includeEnded는 0으로 둔다.
        int includeEnded = progress == null ? 0 : 1;
        FestivalProgressFilter effectiveProgress = progress == null ? FestivalProgressFilter.ALL : progress;
        PageRequest pageRequest = PageRequest.of(page, size);
        LocalDate today = LocalDate.now(SeoulDateTime.ZONE_ID);

        Page<FestivalListProjection> festivalPage = festivalRepository.findVisibleFestivals(
                includeEnded,
                today,
                normalize(keyword),
                normalizeOrNull(sigunguCode),
                startDate,
                endDate,
                effectiveProgress.name(),
                effectiveSort.name(),
                matchableOnly ? 1 : 0,
                FestivalMeetingPointStatus.ACTIVE.name(),
                pageRequest
        );

        List<Long> festivalIds = festivalPage.getContent().stream()
                .map(FestivalListProjection::getId)
                .toList();
        Map<Long, FestivalImage> representativeImages = representativeImages(festivalIds);
        // 찜 수·댓글 수는 정렬에 필요해 쿼리가 이미 집계했고, "내가 찜했는지"만 한 번 더 모은다.
        Set<Long> bookmarkedIds = engagementSummaries.bookmarkedIds(
                ContentTargetType.FESTIVAL, festivalIds, viewerMemberId
        );
        List<FestivalListItemResponse> items = festivalPage.getContent().stream()
                .map(festival -> toResponse(
                        festival,
                        representativeImages.get(festival.getId()),
                        bookmarkedIds.contains(festival.getId())
                ))
                .toList();
        return new FestivalListResponse(
                items,
                festivalPage.getNumber(),
                festivalPage.getSize(),
                festivalPage.getTotalElements(),
                festivalPage.getTotalPages(),
                festivalPage.hasNext(),
                viewerMemberId != null
        );
    }

    /**
     * TourAPI 온디맨드 호출(intro/infoItems/programs)이 끼어 있어 의도적으로 트랜잭션을 걸지
     * 않는다 — DB 조회는 각 repository 메서드가 자체 트랜잭션으로 처리하고, 외부 API 호출 중에는
     * DB 커넥션을 점유하지 않는다.
     */
    public FestivalDetailResponse getFestivalDetail(Long id) {
        Festival festival = festivalRepository.findById(id)
                .filter(found -> found.getStatus() != FestivalStatus.HIDDEN)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "축제를 찾을 수 없습니다."));

        FestivalImage image = festivalImageRepository.findAllByFestivalIdIn(List.of(festival.getId()))
                .stream()
                .findFirst()
                .orElse(null);

        FestivalDetailInfo detailInfo = festivalDetailInfoService.getDetailInfo(
                festival.getContentId(),
                festival.getContentTypeId()
        );

        return new FestivalDetailResponse(
                festival.getId(),
                festival.getContentId(),
                festival.getTitle(),
                festival.getAddress(),
                festival.getAreaCode(),
                festival.getSigunguCode(),
                festival.getEventStartDate(),
                festival.getEventEndDate(),
                festival.getStatus(),
                festival.getMapX(),
                festival.getMapY(),
                image == null ? null : image.getOriginImageUrl(),
                image == null ? null : image.getThumbnailUrl(),
                detailInfo.intro(),
                detailInfo.infoItems(),
                detailInfo.programs()
        );
    }

    @Transactional(readOnly = true)
    public List<NearbyTourPlaceResponse> getNearbyTourPlaces(
            Long festivalId,
            int radiusMeters,
            int limit,
            Long viewerMemberId
    ) {
        Festival festival = festivalRepository.findById(festivalId)
                .filter(found -> found.getStatus() != FestivalStatus.HIDDEN)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "축제를 찾을 수 없습니다."));
        if (festival.getMapX() == null || festival.getMapY() == null) {
            return List.of();
        }

        GeoDistanceCalculator.BoundingBox box = GeoDistanceCalculator.boundingBox(
                festival.getMapY(), festival.getMapX(), radiusMeters
        );

        record Nearby(TourPlace place, long distanceMeters) {
        }
        List<Nearby> nearest = tourPlaceRepository.findAllVisibleWithinBoundingBox(
                        TourPlaceStatus.ACTIVE,
                        box.minLongitude(), box.maxLongitude(),
                        box.minLatitude(), box.maxLatitude()
                ).stream()
                .map(place -> new Nearby(place, GeoDistanceCalculator.metersBetween(
                        festival.getMapY(), festival.getMapX(), place.getMapY(), place.getMapX()
                )))
                .filter(nearby -> nearby.distanceMeters() <= radiusMeters)
                .sorted(Comparator.comparingLong(Nearby::distanceMeters))
                .limit(limit)
                .toList();

        // 집계는 반경·정렬·개수 제한을 모두 끝낸 뒤 최종 목록에 대해서만 한다 — bounding box
        // 후보는 반경 밖 관광지까지 포함하므로 먼저 집계하면 버려질 행까지 세게 된다.
        Map<Long, ContentEngagementSummaryReader.Summary> summaries = engagementSummaries.summarize(
                ContentTargetType.TOUR_PLACE,
                nearest.stream().map(nearby -> nearby.place().getId()).toList(),
                viewerMemberId
        );

        return nearest.stream()
                .map(nearby -> toNearbyResponse(
                        nearby.place(),
                        nearby.distanceMeters(),
                        summaries.getOrDefault(
                                nearby.place().getId(),
                                ContentEngagementSummaryReader.Summary.EMPTY
                        )
                ))
                .toList();
    }

    private NearbyTourPlaceResponse toNearbyResponse(
            TourPlace place,
            long distanceMeters,
            ContentEngagementSummaryReader.Summary summary
    ) {
        return new NearbyTourPlaceResponse(
                place.getId(),
                place.getTitle(),
                place.getAddress(),
                place.getContentTypeId(),
                place.getImageUrl(),
                distanceMeters,
                summary.bookmarkCount(),
                summary.commentCount(),
                summary.bookmarkedByMe()
        );
    }

    private Map<Long, FestivalImage> representativeImages(List<Long> festivalIds) {
        if (festivalIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, FestivalImage> imagesByFestivalId = new LinkedHashMap<>();
        for (FestivalImage image : festivalImageRepository.findAllByFestivalIdIn(festivalIds)) {
            imagesByFestivalId.putIfAbsent(image.getFestivalId(), image);
        }
        return imagesByFestivalId;
    }

    /**
     * PostgreSQL이 {@code lower(concat('%', :keyword, '%'))}에 바인딩되는 null 파라미터의
     * 타입을 추론하지 못해 오류가 나므로(bytea로 오판), null 대신 빈 문자열을 사용해 항상
     * LIKE 패턴을 적용한다. 빈 문자열이면 {@code '%%'}가 되어 모든 제목과 매칭된다.
     */
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }

    /**
     * 단순 동등 비교({@code =})만 하는 파라미터는 위 keyword와 달리 null 바인딩에 문제가 없어
     * {@code :param is null or ...} 패턴을 그대로 쓴다(관광지 contentTypeId와 같은 방식).
     */
    private String normalizeOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** 지역 선택 UI용 시군구 목록. 실제 데이터에 존재하는 지역만 반환한다. */
    @Transactional(readOnly = true)
    public List<RegionOptionResponse> getFestivalRegions() {
        return RegionNameResolver.toOptions(
                festivalRepository.aggregateVisibleRegions(
                        FestivalStatus.ACTIVE,
                        LocalDate.now(SeoulDateTime.ZONE_ID)
                )
        );
    }

    private FestivalListItemResponse toResponse(
            FestivalListProjection festival,
            FestivalImage image,
            boolean bookmarkedByMe
    ) {
        return new FestivalListItemResponse(
                festival.getId(),
                festival.getContentId(),
                festival.getTitle(),
                festival.getAddress(),
                festival.getRegionCode(),
                festival.getSigunguCode(),
                festival.getEventStartDate(),
                festival.getEventEndDate(),
                FestivalStatus.valueOf(festival.getStatus()),
                image == null ? null : image.getOriginImageUrl(),
                image == null ? null : image.getThumbnailUrl(),
                festival.getMapX(),
                festival.getMapY(),
                festival.getBookmarkCount(),
                festival.getCommentCount(),
                bookmarkedByMe
        );
    }
}
