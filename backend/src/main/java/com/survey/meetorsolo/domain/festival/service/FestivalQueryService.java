package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.festival.dto.FestivalDetailInfo;
import com.survey.meetorsolo.domain.festival.dto.FestivalDetailResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalListItemResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalListResponse;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalImage;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalImageRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.tourplace.dto.NearbyTourPlaceResponse;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlace;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import com.survey.meetorsolo.domain.tourplace.repository.TourPlaceRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.geo.GeoDistanceCalculator;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FestivalQueryService {

    private final FestivalRepository festivalRepository;
    private final FestivalImageRepository festivalImageRepository;
    private final TourPlaceRepository tourPlaceRepository;
    private final FestivalDetailInfoService festivalDetailInfoService;

    public FestivalQueryService(
            FestivalRepository festivalRepository,
            FestivalImageRepository festivalImageRepository,
            TourPlaceRepository tourPlaceRepository,
            FestivalDetailInfoService festivalDetailInfoService
    ) {
        this.festivalRepository = festivalRepository;
        this.festivalImageRepository = festivalImageRepository;
        this.tourPlaceRepository = tourPlaceRepository;
        this.festivalDetailInfoService = festivalDetailInfoService;
    }

    @Transactional(readOnly = true)
    public FestivalListResponse getActiveFestivals(int page, int size, String keyword) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("eventStartDate"), Sort.Order.asc("id"))
        );
        LocalDate today = LocalDate.now(SeoulDateTime.ZONE_ID);
        Page<Festival> festivalPage = festivalRepository.findVisibleFestivals(
                FestivalStatus.ACTIVE,
                today,
                normalize(keyword),
                pageRequest
        );

        Map<Long, FestivalImage> representativeImages = representativeImages(
                festivalPage.getContent()
        );
        List<FestivalListItemResponse> items = festivalPage.getContent().stream()
                .map(festival -> toResponse(
                        festival,
                        representativeImages.get(festival.getId())
                ))
                .toList();
        return new FestivalListResponse(
                items,
                festivalPage.getNumber(),
                festivalPage.getSize(),
                festivalPage.getTotalElements(),
                festivalPage.getTotalPages(),
                festivalPage.hasNext()
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
    public List<NearbyTourPlaceResponse> getNearbyTourPlaces(Long festivalId, int radiusMeters, int limit) {
        Festival festival = festivalRepository.findById(festivalId)
                .filter(found -> found.getStatus() != FestivalStatus.HIDDEN)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "축제를 찾을 수 없습니다."));
        if (festival.getMapX() == null || festival.getMapY() == null) {
            return List.of();
        }

        return tourPlaceRepository.findAllVisibleWithCoordinates(TourPlaceStatus.ACTIVE).stream()
                .map(place -> toNearbyResponse(festival, place))
                .filter(response -> response.distanceMeters() <= radiusMeters)
                .sorted(Comparator.comparingLong(NearbyTourPlaceResponse::distanceMeters))
                .limit(limit)
                .toList();
    }

    private NearbyTourPlaceResponse toNearbyResponse(Festival festival, TourPlace place) {
        long distanceMeters = GeoDistanceCalculator.metersBetween(
                festival.getMapY(),
                festival.getMapX(),
                place.getMapY(),
                place.getMapX()
        );
        return new NearbyTourPlaceResponse(
                place.getId(),
                place.getTitle(),
                place.getAddress(),
                place.getContentTypeId(),
                place.getImageUrl(),
                distanceMeters
        );
    }

    private Map<Long, FestivalImage> representativeImages(List<Festival> festivals) {
        if (festivals.isEmpty()) {
            return Map.of();
        }
        List<Long> festivalIds = festivals.stream()
                .map(Festival::getId)
                .toList();
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

    private FestivalListItemResponse toResponse(Festival festival, FestivalImage image) {
        return new FestivalListItemResponse(
                festival.getId(),
                festival.getContentId(),
                festival.getTitle(),
                festival.getAddress(),
                festival.getAreaCode(),
                festival.getSigunguCode(),
                festival.getEventStartDate(),
                festival.getEventEndDate(),
                festival.getStatus(),
                image == null ? null : image.getOriginImageUrl(),
                image == null ? null : image.getThumbnailUrl()
        );
    }
}
