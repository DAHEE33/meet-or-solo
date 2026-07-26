package com.survey.meetorsolo.external.tourapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** TourAPI detailCommon2 응답 — 소개글(overview) 등 공통 상세 정보. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DetailCommonItem(
        @JsonProperty("contentid") String contentId,
        @JsonProperty("contenttypeid") String contentTypeId,
        @JsonProperty("title") String title,
        @JsonProperty("overview") String overview
) {
}
