package com.survey.meetorsolo.domain.festival.dto;

import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPointStatus;
import jakarta.validation.constraints.NotNull;

public record FestivalMeetingPointStatusRequest(@NotNull FestivalMeetingPointStatus status) {
}
