package com.survey.meetorsolo.domain.festival.service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

public record FestivalSyncScope(
        LocalDate syncDate,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        String regionCode,
        Set<String> observedContentIds
) {

    public FestivalSyncScope {
        Objects.requireNonNull(syncDate, "동기화 기준일은 필수입니다.");
        Objects.requireNonNull(eventStartDate, "축제 조회 시작일은 필수입니다.");
        Objects.requireNonNull(eventEndDate, "축제 조회 종료일은 필수입니다.");
        Objects.requireNonNull(regionCode, "축제 조회 지역 코드는 필수입니다.");
        Objects.requireNonNull(observedContentIds, "확인된 관광 콘텐츠 ID 목록은 필수입니다.");
        observedContentIds = Set.copyOf(observedContentIds);
    }
}
