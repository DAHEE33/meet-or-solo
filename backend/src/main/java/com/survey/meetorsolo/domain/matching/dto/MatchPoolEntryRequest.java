package com.survey.meetorsolo.domain.matching.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MatchPoolEntryRequest(
        @NotNull @Positive Long festivalId,
        @NotNull @Min(2) @Max(4) Integer preferredGroupSize,
        @NotNull Boolean allowMinimumTwo,
        @NotNull @Size(max = 0, message = "tags는 현재 빈 배열만 허용합니다.") List<String> tags
) {
}
