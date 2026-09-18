package com.survey.meetorsolo.domain.festival.controller;

import com.survey.meetorsolo.domain.festival.dto.FestivalDetailResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalListResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalListSort;
import com.survey.meetorsolo.domain.festival.dto.FestivalProgressFilter;
import com.survey.meetorsolo.domain.festival.dto.SoloCourseResponse;
import com.survey.meetorsolo.domain.festival.dto.SoloCourseType;
import com.survey.meetorsolo.domain.festival.service.FestivalQueryService;
import com.survey.meetorsolo.domain.festival.service.SoloCourseService;
import com.survey.meetorsolo.domain.content.support.OptionalMemberResolver;
import com.survey.meetorsolo.domain.tourplace.dto.NearbyTourPlaceResponse;
import com.survey.meetorsolo.global.region.RegionOptionResponse;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/festivals")
public class FestivalController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final FestivalQueryService festivalQueryService;
    private final SoloCourseService soloCourseService;
    private final OptionalMemberResolver optionalMember;

    public FestivalController(
            FestivalQueryService festivalQueryService,
            SoloCourseService soloCourseService,
            OptionalMemberResolver optionalMember
    ) {
        this.festivalQueryService = festivalQueryService;
        this.soloCourseService = soloCourseService;
        this.optionalMember = optionalMember;
    }

    /**
     * 축제 목록. 모든 필터는 선택이다.
     *
     * <p>{@code startDate}/{@code endDate}는 "그 기간에 열리는 축제"를 찾는 기간 선택이며 축제
     * 기간과 겹치면 걸린다. 한쪽만 넘겨도 된다.
     *
     * <p><b>{@code progress}를 넘기면 종료된 축제까지 검색된다.</b> 넘기지 않으면 기존과 같이
     * 진행 중·예정 축제만 나온다 — 같은 API를 홈 화면과 관광지 상세가 함께 쓰기 때문에 기본
     * 동작을 바꾸지 않는다(docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md).
     *
     * <p><b>공개 조회다.</b> 로그인했으면 목록 카드의 찜 상태({@code items[].bookmarkedByMe})와
     * {@code viewerLoggedIn}을 함께 채워 주지만, 비로그인·만료 토큰도 그대로 {@code 200}이다 —
     * 여기서 {@code 401}을 내면 frontend {@code apiClient}의 전역 리다이렉트 때문에 탐색 화면을
     * 열기만 해도 로그인으로 튕긴다(docs/27 2.1).
     */
    @GetMapping
    public ApiResponse<FestivalListResponse> getFestivals(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sigunguCode,
            @RequestParam(defaultValue = "RECENTLY_ADDED") FestivalListSort sort,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) FestivalProgressFilter progress,
            @RequestParam(defaultValue = "false") boolean matchableOnly
    ) {
        return ApiResponse.success(festivalQueryService.getActiveFestivals(
                page, size, keyword, sigunguCode, sort, startDate, endDate, progress, matchableOnly,
                optionalMember.resolveOrNull(accessToken)
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

    /**
     * "축제와 함께 둘러보기". 카드에서 바로 찜을 토글하므로 찜 수·댓글 수와 내 찜 여부를 함께
     * 내려준다. 공개 조회라 비로그인도 {@code 200}이다.
     */
    @GetMapping("/{id}/nearby-spots")
    public ApiResponse<List<NearbyTourPlaceResponse>> getNearbyTourPlaces(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable Long id,
            @RequestParam(defaultValue = "5000")
            @Min(value = 100, message = "radiusMeters는 100 이상이어야 합니다.")
            @Max(value = 20000, message = "radiusMeters는 20000 이하여야 합니다.") int radiusMeters,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "limit은 1 이상이어야 합니다.")
            @Max(value = 50, message = "limit은 50 이하여야 합니다.") int limit
    ) {
        return ApiResponse.success(festivalQueryService.getNearbyTourPlaces(
                id, radiusMeters, limit, optionalMember.resolveOrNull(accessToken)
        ));
    }

    @GetMapping("/{id}/solo-course")
    public ApiResponse<SoloCourseResponse> getSoloCourse(
            @PathVariable Long id,
            @RequestParam(defaultValue = "HALF") SoloCourseType type
    ) {
        return ApiResponse.success(soloCourseService.getSoloCourse(id, type));
    }
}
