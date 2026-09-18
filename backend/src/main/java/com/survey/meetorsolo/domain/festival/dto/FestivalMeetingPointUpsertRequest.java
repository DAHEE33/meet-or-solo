package com.survey.meetorsolo.domain.festival.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record FestivalMeetingPointUpsertRequest(
        @NotBlank @Size(max = 50) String kakaoPlaceId,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 500) String address,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @PositiveOrZero Integer assignmentOrder
) {
}
