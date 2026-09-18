package com.survey.meetorsolo.external.tourapi.dto;

import java.util.Objects;

/** detailCommon2/detailIntro2/detailInfo2 공통 요청 파라미터. */
public record FestivalDetailApiRequest(String contentId, String contentTypeId) {

    public FestivalDetailApiRequest {
        Objects.requireNonNull(contentId, "contentId는 필수입니다.");
        Objects.requireNonNull(contentTypeId, "contentTypeId는 필수입니다.");
        if (contentId.isBlank()) {
            throw new IllegalArgumentException("contentId는 공백일 수 없습니다.");
        }
        if (contentTypeId.isBlank()) {
            throw new IllegalArgumentException("contentTypeId는 공백일 수 없습니다.");
        }
    }
}
