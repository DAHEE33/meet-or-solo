package com.survey.meetorsolo.external.naver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NaverUserResponse(
        String resultcode,
        String message,
        Profile response
) {
    public String providerUserId() {
        return response == null ? null : response.id();
    }

    public String email() {
        return response == null ? null : response.email();
    }

    public String nickname() {
        return response == null ? null : response.nickname();
    }

    public String profileImageUrl() {
        return response == null ? null : response.profileImage();
    }

    public String gender() {
        return response == null ? null : response.gender();
    }

    public String age() {
        return response == null ? null : response.age();
    }

    public String birthyear() {
        return response == null ? null : response.birthyear();
    }

    public record Profile(
            String id,
            String email,
            String nickname,
            @JsonProperty("profile_image") String profileImage,
            String gender,
            String age,
            String birthyear,
            String mobile
    ) {
    }
}
