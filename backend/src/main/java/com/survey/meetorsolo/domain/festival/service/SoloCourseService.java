package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.festival.dto.SoloCourseResponse;
import com.survey.meetorsolo.domain.festival.dto.SoloCourseStopResponse;
import com.survey.meetorsolo.domain.festival.dto.SoloCourseType;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlace;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import com.survey.meetorsolo.domain.tourplace.repository.TourPlaceRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.geo.GeoDistanceCalculator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체크인한 축제 좌표를 시작점으로, 반경 내 관광지를 greedy nearest-neighbor로 이어붙인 솔로 코스를
 * 만든다. 새 TourAPI 호출이나 Flyway migration 없이 기존 동기화 데이터만 사용한다.
 * docs/23_SOLO_COURSE_ITINERARY_DESIGN.md 참고.
 */
@Service
public class SoloCourseService {

    /** 후보 pool 반경 — 기존 nearby-spots API의 기본값과 동일하게 맞춘다. */
    private static final int CANDIDATE_RADIUS_METERS = 5_000;

    private final FestivalRepository festivalRepository;
    private final TourPlaceRepository tourPlaceRepository;
    private final SoloCourseStayPolicy stayPolicy;

    public SoloCourseService(
            FestivalRepository festivalRepository,
            TourPlaceRepository tourPlaceRepository,
            SoloCourseStayPolicy stayPolicy
    ) {
        this.festivalRepository = festivalRepository;
        this.tourPlaceRepository = tourPlaceRepository;
        this.stayPolicy = stayPolicy;
    }

    @Transactional(readOnly = true)
    public SoloCourseResponse getSoloCourse(Long festivalId, SoloCourseType type) {
        Festival festival = festivalRepository.findById(festivalId)
                .filter(found -> found.getStatus() != FestivalStatus.HIDDEN)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "축제를 찾을 수 없습니다."));
        if (festival.getMapX() == null || festival.getMapY() == null) {
            return SoloCourseResponse.of(type, List.of());
        }

        List<TourPlace> candidates = new ArrayList<>(
                tourPlaceRepository.findAllVisibleWithCoordinates(TourPlaceStatus.ACTIVE).stream()
                        .filter(place -> distanceMeters(festival.getMapY(), festival.getMapX(), place)
                                <= CANDIDATE_RADIUS_METERS)
                        .toList()
        );

        return SoloCourseResponse.of(type, buildStops(festival, candidates, type));
    }

    private List<SoloCourseStopResponse> buildStops(Festival festival, List<TourPlace> candidates, SoloCourseType type) {
        List<SoloCourseStopResponse> stops = new ArrayList<>();
        BigDecimal currentLat = festival.getMapY();
        BigDecimal currentLon = festival.getMapX();
        String previousContentTypeId = null;
        int remainingBudget = stayPolicy.budgetMinutes(type);

        while (!candidates.isEmpty() && stops.size() < SoloCourseStayPolicy.MAX_STOPS) {
            BigDecimal fromLat = currentLat;
            BigDecimal fromLon = currentLon;
            int budgetSnapshot = remainingBudget;

            List<TourPlace> feasible = candidates.stream()
                    .filter(place -> {
                        long hop = distanceMeters(fromLat, fromLon, place);
                        if (hop > SoloCourseStayPolicy.MAX_HOP_METERS) return false;
                        int walkMinutes = stayPolicy.walkMinutes(hop);
                        int stayMinutes = stayPolicy.stayMinutes(place.getContentTypeId());
                        return walkMinutes + stayMinutes <= budgetSnapshot;
                    })
                    .sorted(Comparator.comparingLong(place -> distanceMeters(fromLat, fromLon, place)))
                    .toList();
            if (feasible.isEmpty()) {
                break;
            }

            TourPlace nearest = feasible.get(0);
            TourPlace chosen = chooseWithDiversity(feasible, nearest, previousContentTypeId, fromLat, fromLon);

            long hop = distanceMeters(fromLat, fromLon, chosen);
            int walkMinutes = stayPolicy.walkMinutes(hop);
            int stayMinutes = stayPolicy.stayMinutes(chosen.getContentTypeId());
            stops.add(new SoloCourseStopResponse(
                    stops.size() + 1,
                    chosen.getId(),
                    chosen.getTitle(),
                    chosen.getAddress(),
                    chosen.getContentTypeId(),
                    chosen.getImageUrl(),
                    hop,
                    walkMinutes,
                    stayMinutes
            ));

            remainingBudget -= (walkMinutes + stayMinutes);
            currentLat = chosen.getMapY();
            currentLon = chosen.getMapX();
            previousContentTypeId = chosen.getContentTypeId();
            candidates.remove(chosen);
        }
        return stops;
    }

    /**
     * 가장 가까운 후보가 직전 스톱과 같은 카테고리면, {@code DIVERSITY_TOLERANCE} 배율 이내에
     * 다른 카테고리 대안이 있는지 찾아 대신 선택한다. 대안이 없으면 원래 가장 가까운 후보를
     * 그대로 선택한다(다양성 때문에 억지로 먼 곳까지 끌고 가지 않는다). 첫 스톱에는 적용하지 않는다.
     */
    private TourPlace chooseWithDiversity(
            List<TourPlace> feasibleSortedByDistance,
            TourPlace nearest,
            String previousContentTypeId,
            BigDecimal fromLat,
            BigDecimal fromLon
    ) {
        if (previousContentTypeId == null || !nearest.getContentTypeId().equals(previousContentTypeId)) {
            return nearest;
        }
        long nearestDistance = distanceMeters(fromLat, fromLon, nearest);
        long toleratedDistance = Math.round(nearestDistance * SoloCourseStayPolicy.DIVERSITY_TOLERANCE);
        return feasibleSortedByDistance.stream()
                .filter(place -> !place.getContentTypeId().equals(previousContentTypeId))
                .filter(place -> distanceMeters(fromLat, fromLon, place) <= toleratedDistance)
                .findFirst()
                .orElse(nearest);
    }

    private long distanceMeters(BigDecimal fromLat, BigDecimal fromLon, TourPlace place) {
        return GeoDistanceCalculator.metersBetween(fromLat, fromLon, place.getMapY(), place.getMapX());
    }
}
