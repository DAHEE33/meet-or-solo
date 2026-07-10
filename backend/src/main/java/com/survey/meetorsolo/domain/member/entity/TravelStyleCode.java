package com.survey.meetorsolo.domain.member.entity;

public enum TravelStyleCode {

    RELAXED("느긋하게"),
    ACTIVE("액티브"),
    FOOD("맛집탐방"),
    PHOTO("사진위주"),
    CULTURE("문화답사");

    private final String label;

    TravelStyleCode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
