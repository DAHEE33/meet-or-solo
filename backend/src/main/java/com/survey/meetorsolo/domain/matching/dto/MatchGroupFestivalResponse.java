package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository.ActiveGroupWithFestivalProjection;
import java.time.LocalDate;

public record MatchGroupFestivalResponse(
        Long festivalId,
        String title,
        String address,
        LocalDate eventStartDate,
        LocalDate eventEndDate
) {

    public static MatchGroupFestivalResponse from(ActiveGroupWithFestivalProjection group) {
        return new MatchGroupFestivalResponse(
                group.getFestivalId(),
                group.getFestivalTitle(),
                group.getFestivalAddress(),
                group.getFestivalEventStartDate(),
                group.getFestivalEventEndDate()
        );
    }
}
