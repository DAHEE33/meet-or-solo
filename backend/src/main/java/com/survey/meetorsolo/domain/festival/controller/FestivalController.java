package com.survey.meetorsolo.domain.festival.controller;

import com.survey.meetorsolo.domain.festival.dto.FestivalListResponse;
import com.survey.meetorsolo.domain.festival.service.FestivalQueryService;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
}
