package com.survey.meetorsolo.external.tourapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchTourPlaceItem(
        @JsonProperty("addr1") String address1,
        @JsonProperty("addr2") String address2,
        @JsonProperty("zipcode") String zipcode,
        @JsonProperty("contentid") String contentId,
        @JsonProperty("contenttypeid") String contentTypeId,
        @JsonProperty("createdtime") String createdTime,
        @JsonProperty("firstimage") String firstImage,
        @JsonProperty("firstimage2") String firstImageThumbnail,
        @JsonProperty("cpyrhtDivCd") String copyrightType,
        @JsonProperty("mapx") String mapX,
        @JsonProperty("mapy") String mapY,
        @JsonProperty("mlevel") String mapLevel,
        @JsonProperty("modifiedtime") String modifiedTime,
        @JsonProperty("tel") String telephone,
        @JsonProperty("title") String title,
        @JsonProperty("cat1") String category1,
        @JsonProperty("cat2") String category2,
        @JsonProperty("cat3") String category3,
        @JsonProperty("lDongRegnCd") String regionCode,
        @JsonProperty("lDongSignguCd") String sigunguCode
) {
}
