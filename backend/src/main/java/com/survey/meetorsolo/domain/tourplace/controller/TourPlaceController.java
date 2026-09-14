package com.survey.meetorsolo.domain.tourplace.controller;

import com.survey.meetorsolo.domain.content.support.OptionalMemberResolver;
import com.survey.meetorsolo.domain.festival.dto.NearbyFestivalResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceDetailResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListSort;
import com.survey.meetorsolo.domain.tourplace.service.TourPlaceQueryService;
import com.survey.meetorsolo.global.region.RegionOptionResponse;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/spots")
public class TourPlaceController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final TourPlaceQueryService tourPlaceQueryService;
    private final OptionalMemberResolver optionalMember;

    public TourPlaceController(
            TourPlaceQueryService tourPlaceQueryService,
            OptionalMemberResolver optionalMember
    ) {
        this.tourPlaceQueryService = tourPlaceQueryService;
        this.optionalMember = optionalMember;
    }

    /**
     * 관광지 목록. 거리 필터·거리 정렬은 제공하지 않는다 — 사용자 좌표를 서버로 받지 않기
     * 때문이다(docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 3.1). "내 주변" 성격의 조회는
     * 축제 좌표를 중심으로 하는 {@code GET /api/festivals/{id}/nearby-spots}가 담당한다.
     *
     * <p><b>공개 조회다.</b> 축제 목록과 같은 이유로 비로그인·만료 토큰도 {@code 200}이며,
     * 로그인했을 때만 찜 상태가 채워진다(docs/27 2.1).
     */
    @GetMapping
    public ApiResponse<TourPlaceListResponse> getTourPlaces(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.") int size,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sigunguCode,
            @RequestParam(defaultValue = "TITLE_ASC") TourPlaceListSort sort
    ) {
        return ApiResponse.success(tourPlaceQueryService.getVisiblePlaces(
                page, size, contentTypeId, keyword, sigunguCode, sort,
                optionalMember.resolveOrNull(accessToken)
        ));
    }

    /** 지역 선택 UI용 시군구 목록. 카테고리를 넘기면 그 카테고리에 장소가 있는 지역만 내려간다. */
    @GetMapping("/regions")
    public ApiResponse<List<RegionOptionResponse>> getTourPlaceRegions(
            @RequestParam(required = false) String contentTypeId
    ) {
        return ApiResponse.success(tourPlaceQueryService.getTourPlaceRegions(contentTypeId));
    }

    @GetMapping("/{id}")
    public ApiResponse<TourPlaceDetailResponse> getTourPlace(@PathVariable Long id) {
        return ApiResponse.success(tourPlaceQueryService.getTourPlaceDetail(id));
    }

    /**
     * "이 장소 주변에서 열리는 축제". 카드에서 바로 찜을 토글하므로 찜 수·댓글 수와 내 찜
     * 여부를 함께 내려준다. 공개 조회라 비로그인도 {@code 200}이다.
     */
    @GetMapping("/{id}/nearby-festivals")
    public ApiResponse<List<NearbyFestivalResponse>> getNearbyFestivals(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable Long id,
            @RequestParam(defaultValue = "5000")
            @Min(value = 100, message = "radiusMeters는 100 이상이어야 합니다.")
            @Max(value = 20000, message = "radiusMeters는 20000 이하여야 합니다.") int radiusMeters,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "limit은 1 이상이어야 합니다.")
            @Max(value = 50, message = "limit은 50 이하여야 합니다.") int limit
    ) {
        return ApiResponse.success(tourPlaceQueryService.getNearbyFestivals(
                id, radiusMeters, limit, optionalMember.resolveOrNull(accessToken)
        ));
    }
}
