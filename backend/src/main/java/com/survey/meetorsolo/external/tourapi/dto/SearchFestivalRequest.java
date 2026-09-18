package com.survey.meetorsolo.external.tourapi.dto;

import java.time.LocalDate;
import java.util.Objects;

public record SearchFestivalRequest(
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        LocalDate modifiedTime,
        int pageNo,
        int numOfRows,
        TourApiArrange arrange,
        String regionCode,
        String sigunguCode,
        String classificationSystem1,
        String classificationSystem2,
        String classificationSystem3
) {

    public SearchFestivalRequest {
        Objects.requireNonNull(eventStartDate, "행사 시작일은 필수입니다.");
        if (eventEndDate != null && eventEndDate.isBefore(eventStartDate)) {
            throw new IllegalArgumentException("행사 종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (pageNo < 1) {
            throw new IllegalArgumentException("페이지 번호는 1 이상이어야 합니다.");
        }
        if (numOfRows < 1) {
            throw new IllegalArgumentException("페이지 결과 수는 1 이상이어야 합니다.");
        }

        arrange = arrange == null ? TourApiArrange.MODIFIED : arrange;
        regionCode = normalize(regionCode);
        sigunguCode = normalize(sigunguCode);
        classificationSystem1 = normalize(classificationSystem1);
        classificationSystem2 = normalize(classificationSystem2);
        classificationSystem3 = normalize(classificationSystem3);

        if (sigunguCode != null && regionCode == null) {
            throw new IllegalArgumentException("시군구 코드를 사용하려면 시도 코드가 필요합니다.");
        }
        if (classificationSystem2 != null && classificationSystem1 == null) {
            throw new IllegalArgumentException("중분류를 사용하려면 대분류가 필요합니다.");
        }
        if (classificationSystem3 != null
                && (classificationSystem1 == null || classificationSystem2 == null)) {
            throw new IllegalArgumentException("소분류를 사용하려면 대분류와 중분류가 필요합니다.");
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
