package com.survey.meetorsolo.domain.tourplace.service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

public record TourPlaceSyncScope(
        LocalDate syncDate,
        String contentTypeId,
        Set<String> observedContentIds
) {

    public TourPlaceSyncScope {
        Objects.requireNonNull(syncDate, "동기화 기준일은 필수입니다.");
        Objects.requireNonNull(contentTypeId, "관광지 콘텐츠 타입 ID는 필수입니다.");
        Objects.requireNonNull(observedContentIds, "확인된 관광 콘텐츠 ID 목록은 필수입니다.");
        observedContentIds = Set.copyOf(observedContentIds);
    }
}
