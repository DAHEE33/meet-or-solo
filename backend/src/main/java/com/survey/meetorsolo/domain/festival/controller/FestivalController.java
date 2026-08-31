package com.survey.meetorsolo.domain.festival.controller;

import com.survey.meetorsolo.domain.festival.dto.FestivalDetailResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalListResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalListSort;
import com.survey.meetorsolo.domain.festival.dto.FestivalScheduleFilter;
import com.survey.meetorsolo.domain.festival.dto.SoloCourseResponse;
import com.survey.meetorsolo.domain.festival.dto.SoloCourseType;
import com.survey.meetorsolo.domain.festival.service.FestivalQueryService;
import com.survey.meetorsolo.domain.festival.service.SoloCourseService;
import com.survey.meetorsolo.domain.tourplace.dto.NearbyTourPlaceResponse;
import com.survey.meetorsolo.global.region.RegionOptionResponse;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/festivals")
public class FestivalController {

    private final FestivalQueryService festivalQueryService;
    private final SoloCourseService soloCourseService;

    public FestivalController(FestivalQueryService festivalQueryService, SoloCourseService soloCourseService) {
        this.festivalQueryService = festivalQueryService;
        this.soloCourseService = soloCourseService;
    }

    /**
     * 축제 목록. 정렬·일정·지역 파라미터는 모두 선택이며, 아무것도 넘기지 않으면 기존과 동일한
     * 결과(시작일 오름차순, 필터 없음)를 반환한다.
     */
    @GetMapping
    public ApiResponse<FestivalListResponse> getFestivals(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sigunguCode,
            @RequestParam(defaultValue = "START_DATE_ASC") FestivalListSort sort,
            @RequestParam(defaultValue = "ALL") FestivalScheduleFilter schedule,
            @RequestParam(defaultValue = "false") boolean matchableOnly
    ) {
        return ApiResponse.success(festivalQueryService.getActiveFestivals(
                page, size, keyword, sigunguCode, sort, schedule, matchableOnly
        ));
    }

    /** 지역 선택 UI용 시군구 목록. 실제로 축제가 있는 지역만 내려간다. */
    @GetMapping("/regions")
    public ApiResponse<List<RegionOptionResponse>> getFestivalRegions() {
        return ApiResponse.success(festivalQueryService.getFestivalRegions());
    }

    @GetMapping("/{id}")
    public ApiResponse<FestivalDetailResponse> getFestival(@PathVariable Long id) {
        return ApiResponse.success(festivalQueryService.getFestivalDetail(id));
    }

    @GetMapping("/{id}/nearby-spots")
    public ApiResponse<List<NearbyTourPlaceResponse>> getNearbyTourPlaces(
            @PathVariable Long id,
            @RequestParam(defaultValue = "5000")
            @Min(value = 100, message = "radiusMeters는 100 이상이어야 합니다.")
            @Max(value = 20000, message = "radiusMeters는 20000 이하여야 합니다.") int radiusMeters,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "limit은 1 이상이어야 합니다.")
            @Max(value = 50, message = "limit은 50 이하여야 합니다.") int limit
    ) {
        return ApiResponse.success(
                festivalQueryService.getNearbyTourPlaces(id, radiusMeters, limit)
        );
    }

    @GetMapping("/{id}/solo-course")
    public ApiResponse<SoloCourseResponse> getSoloCourse(
            @PathVariable Long id,
            @RequestParam(defaultValue = "HALF") SoloCourseType type
    ) {
        return ApiResponse.success(soloCourseService.getSoloCourse(id, type));
    }
}
