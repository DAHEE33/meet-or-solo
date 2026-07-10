package com.survey.meetorsolo.domain.member.dto;

import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;

public record TravelStyleResponse(
        String code,
        String label
) {

    public static TravelStyleResponse from(TravelStyleCode styleCode) {
        return new TravelStyleResponse(styleCode.name(), styleCode.getLabel());
    }
}
