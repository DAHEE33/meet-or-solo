package com.survey.meetorsolo.domain.matching.dto;

import jakarta.validation.constraints.NotNull;

public record MatchCancellationRequest(@NotNull MatchCancellationReason reason) {
}
