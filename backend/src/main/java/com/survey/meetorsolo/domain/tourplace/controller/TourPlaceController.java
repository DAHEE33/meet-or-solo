package com.survey.meetorsolo.domain.tourplace.controller;

import com.survey.meetorsolo.domain.festival.dto.NearbyFestivalResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceDetailResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListResponse;
import com.survey.meetorsolo.domain.tourplace.service.TourPlaceQueryService;
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
@RequestMapping("/api/spots")
public class TourPlaceController {

    private final TourPlaceQueryService tourPlaceQueryService;

    public TourPlaceController(TourPlaceQueryService tourPlaceQueryService) {
        this.tourPlaceQueryService = tourPlaceQueryService;
    }

    @GetMapping
    public ApiResponse<TourPlaceListResponse> getTourPlaces(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.") int size,
            @RequestParam(required = false) String contentTypeId
    ) {
        return ApiResponse.success(
                tourPlaceQueryService.getVisiblePlaces(page, size, contentTypeId)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<TourPlaceDetailResponse> getTourPlace(@PathVariable Long id) {
        return ApiResponse.success(tourPlaceQueryService.getTourPlaceDetail(id));
    }

    @GetMapping("/{id}/nearby-festivals")
    public ApiResponse<List<NearbyFestivalResponse>> getNearbyFestivals(
            @PathVariable Long id,
            @RequestParam(defaultValue = "5000")
            @Min(value = 100, message = "radiusMeters는 100 이상이어야 합니다.")
            @Max(value = 20000, message = "radiusMeters는 20000 이하여야 합니다.") int radiusMeters,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "limit은 1 이상이어야 합니다.")
            @Max(value = 50, message = "limit은 50 이하여야 합니다.") int limit
    ) {
        return ApiResponse.success(
                tourPlaceQueryService.getNearbyFestivals(id, radiusMeters, limit)
        );
    }
}
