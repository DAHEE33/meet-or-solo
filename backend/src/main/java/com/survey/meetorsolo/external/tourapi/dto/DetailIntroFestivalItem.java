package com.survey.meetorsolo.external.tourapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** TourAPI detailIntro2 응답 — 축제(contentTypeId=15) 전용 이용정보 필드셋. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DetailIntroFestivalItem(
        @JsonProperty("contentid") String contentId,
        @JsonProperty("playtime") String playTime,
        @JsonProperty("usetimefestival") String useTimeFestival,
        @JsonProperty("discountinfofestival") String discountInfo,
        @JsonProperty("spendtimefestival") String spendTime,
        @JsonProperty("sponsor1") String sponsor1,
        @JsonProperty("sponsor1tel") String sponsor1Tel,
        @JsonProperty("sponsor2") String sponsor2,
        @JsonProperty("sponsor2tel") String sponsor2Tel,
        @JsonProperty("eventplace") String eventPlace,
        @JsonProperty("agelimit") String ageLimit,
        @JsonProperty("bookingplace") String bookingPlace
) {
}
