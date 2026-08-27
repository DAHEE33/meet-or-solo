package com.survey.meetorsolo.domain.tourplace.service;

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
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.geo.GeoDistanceCalculator;
import com.survey.meetorsolo.global.region.RegionNameResolver;
import com.survey.meetorsolo.global.region.RegionOptionResponse;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TourPlaceQueryService {

    private final TourPlaceRepository tourPlaceRepository;
    private final FestivalRepository festivalRepository;
    private final FestivalImageRepository festivalImageRepository;

    public TourPlaceQueryService(
            TourPlaceRepository tourPlaceRepository,
            FestivalRepository festivalRepository,
            FestivalImageRepository festivalImageRepository
    ) {
        this.tourPlaceRepository = tourPlaceRepository;
        this.festivalRepository = festivalRepository;
        this.festivalImageRepository = festivalImageRepository;
    }

    @Transactional(readOnly = true)
    public TourPlaceListResponse getVisiblePlaces(
            int page,
            int size,
            String contentTypeId,
            String keyword,
            String sigunguCode,
            TourPlaceListSort sort
    ) {
        TourPlaceListSort effectiveSort = sort == null ? TourPlaceListSort.TITLE_ASC : sort;
        PageRequest pageRequest = PageRequest.of(page, size, effectiveSort.sort());
        Page<TourPlaceListItemResponse> placePage = tourPlaceRepository.findVisiblePlaces(
                TourPlaceStatus.ACTIVE,
                normalizeOrNull(contentTypeId),
                normalizeOrNull(sigunguCode),
                normalizeKeyword(keyword),
                pageRequest
        );

        return new TourPlaceListResponse(
                placePage.getContent(),
                placePage.getNumber(),
                placePage.getSize(),
                placePage.getTotalElements(),
                placePage.getTotalPages(),
                placePage.hasNext()
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

    @Transactional(readOnly = true)
    public List<NearbyFestivalResponse> getNearbyFestivals(Long tourPlaceId, int radiusMeters, int limit) {
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
        List<Long> festivalIds = candidates.stream().map(Festival::getId).toList();
        var thumbnailsByFestivalId = festivalImageRepository.findAllByFestivalIdIn(festivalIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        FestivalImage::getFestivalId,
                        FestivalImage::getThumbnailUrl,
                        (first, second) -> first
                ));

        return candidates.stream()
                .map(festival -> toNearbyResponse(place, festival, thumbnailsByFestivalId.get(festival.getId())))
                .filter(response -> response.distanceMeters() <= radiusMeters)
                .sorted(Comparator.comparingLong(NearbyFestivalResponse::distanceMeters))
                .limit(limit)
                .toList();
    }

    private NearbyFestivalResponse toNearbyResponse(TourPlace place, Festival festival, String thumbnailUrl) {
        long distanceMeters = GeoDistanceCalculator.metersBetween(
                place.getMapY(),
                place.getMapX(),
                festival.getMapY(),
                festival.getMapX()
        );
        return new NearbyFestivalResponse(
                festival.getId(),
                festival.getTitle(),
                festival.getAddress(),
                festival.getEventStartDate(),
                festival.getEventEndDate(),
                festival.getStatus(),
                thumbnailUrl,
                distanceMeters
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
