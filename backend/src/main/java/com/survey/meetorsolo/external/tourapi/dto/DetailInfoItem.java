package com.survey.meetorsolo.external.tourapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** TourAPI detailInfo2 응답 — 축제 프로그램/세부 일정 반복 항목. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DetailInfoItem(
        @JsonProperty("contentid") String contentId,
        @JsonProperty("serialnum") String serialNumber,
        @JsonProperty("infoname") String infoName,
        @JsonProperty("infotext") String infoText
) {
}
