package com.survey.meetorsolo.external.tourapi.dto;

import java.util.Objects;

public record SearchTourPlaceRequest(
        String contentTypeId,
        int pageNo,
        int numOfRows,
        TourApiArrange arrange,
        String regionCode,
        String sigunguCode
) {

    public SearchTourPlaceRequest {
        Objects.requireNonNull(contentTypeId, "관광지 콘텐츠 타입 ID는 필수입니다.");
        if (pageNo < 1) {
            throw new IllegalArgumentException("페이지 번호는 1 이상이어야 합니다.");
        }
        if (numOfRows < 1) {
            throw new IllegalArgumentException("페이지 결과 수는 1 이상이어야 합니다.");
        }

        arrange = arrange == null ? TourApiArrange.MODIFIED : arrange;
        regionCode = normalize(regionCode);
        sigunguCode = normalize(sigunguCode);

        if (sigunguCode != null && regionCode == null) {
            throw new IllegalArgumentException("시군구 코드를 사용하려면 시도 코드가 필요합니다.");
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
