package com.survey.meetorsolo.domain.tourplace.dto;

/** 축제 상세 "축제와 함께 둘러보기" 등에서 사용하는, 기준 좌표 주변 관광지 응답. */
public record NearbyTourPlaceResponse(
        Long id,
        String title,
        String address,
        String contentTypeId,
        String imageUrl,
        long distanceMeters
) {
}
