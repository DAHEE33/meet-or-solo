package com.survey.meetorsolo.domain.matching.dto;

import jakarta.validation.constraints.NotNull;

public record MatchArrivalTimeRequest(
        @NotNull @ArrivalMinutes Integer arrivalMinutes
) {
}
