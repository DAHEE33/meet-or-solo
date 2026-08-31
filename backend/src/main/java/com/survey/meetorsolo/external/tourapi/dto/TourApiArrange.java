package com.survey.meetorsolo.external.tourapi.dto;

public enum TourApiArrange {
    TITLE("A"),
    MODIFIED("C"),
    CREATED("D"),
    IMAGE_TITLE("O"),
    IMAGE_MODIFIED("Q"),
    IMAGE_CREATED("R");

    private final String code;

    TourApiArrange(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
