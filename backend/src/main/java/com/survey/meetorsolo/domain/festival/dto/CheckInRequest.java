package com.survey.meetorsolo.domain.festival.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 브라우저 Geolocation API가 준 좌표를 그대로 담아 서버로 보낸다. 서버는 거리 계산에만 쓰고
 * 응답/DB 어디에도 원본 좌표를 남기지 않는다.
 */
public record CheckInRequest(
        @NotNull(message = "latitude는 필수입니다.")
        @DecimalMin(value = "-90", message = "latitude는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90", message = "latitude는 90 이하여야 합니다.")
        BigDecimal latitude,

        @NotNull(message = "longitude는 필수입니다.")
        @DecimalMin(value = "-180", message = "longitude는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180", message = "longitude는 180 이하여야 합니다.")
        BigDecimal longitude,

        @Min(value = 0, message = "accuracyMeters는 0 이상이어야 합니다.")
        Integer accuracyMeters
) {
}
