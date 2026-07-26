package com.survey.meetorsolo.domain.festival.controller;

import com.survey.meetorsolo.domain.festival.dto.FestivalDetailResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalListResponse;
import com.survey.meetorsolo.domain.festival.service.FestivalQueryService;
import com.survey.meetorsolo.domain.tourplace.dto.NearbyTourPlaceResponse;
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

    public FestivalController(FestivalQueryService festivalQueryService) {
        this.festivalQueryService = festivalQueryService;
    }

    @GetMapping
    public ApiResponse<FestivalListResponse> getFestivals(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    ) {
        return ApiResponse.success(festivalQueryService.getActiveFestivals(page, size));
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
}
