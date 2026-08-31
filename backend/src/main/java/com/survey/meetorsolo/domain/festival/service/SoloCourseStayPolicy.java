package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.festival.dto.SoloCourseType;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 체크인 기반 솔로 코스(동선) 정책 상수. {@code tour_places}에는 실제 영업시간·체류시간 데이터가
 * 없어 {@code contentTypeId}별 고정 추정치를 쓴다. docs/23_SOLO_COURSE_ITINERARY_DESIGN.md 참고.
 */
@Component
public class SoloCourseStayPolicy {

    /** 도보 속도 약 4km/h 가정. frontend utils/tourSpot.ts의 formatWalkMinutesLabel과 동일 가정. */
    private static final int METERS_PER_WALK_MINUTE = 67;

    private static final Map<String, Integer> STAY_MINUTES_BY_CONTENT_TYPE_ID = Map.of(
            "12", 60, // 관광지
            "14", 45, // 문화시설
            "28", 90, // 액티비티
            "39", 50  // 맛집
    );
    private static final int DEFAULT_STAY_MINUTES = 60;

    private static final int HALF_BUDGET_MINUTES = 240;
    private static final int FULL_BUDGET_MINUTES = 480;

    /** 스톱 간 한 번의 이동(hop)이 이 거리를 넘으면 그 후보는 이번 라운드에서 제외한다. */
    public static final int MAX_HOP_METERS = 1_500;

    /** 코스가 한없이 늘어나지 않도록 두는 최대 스톱 개수 상한. */
    public static final int MAX_STOPS = 6;

    /**
     * 가장 가까운 후보가 직전 스톱과 같은 카테고리일 때, 이 배율 이내의 거리에 다른 카테고리
     * 대안이 있으면 그 대안을 대신 선택한다.
     */
    public static final double DIVERSITY_TOLERANCE = 1.5;

    public int budgetMinutes(SoloCourseType type) {
        return type == SoloCourseType.FULL ? FULL_BUDGET_MINUTES : HALF_BUDGET_MINUTES;
    }

    public int walkMinutes(long distanceMeters) {
        return Math.max(1, (int) Math.ceil(distanceMeters / (double) METERS_PER_WALK_MINUTE));
    }

    public int stayMinutes(String contentTypeId) {
        return STAY_MINUTES_BY_CONTENT_TYPE_ID.getOrDefault(contentTypeId, DEFAULT_STAY_MINUTES);
    }
}
