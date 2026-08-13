package com.survey.meetorsolo.domain.safety.block.dto;

import jakarta.validation.constraints.Positive;

public record MatchBlockRequest(@Positive long blockedMemberId) {
}
