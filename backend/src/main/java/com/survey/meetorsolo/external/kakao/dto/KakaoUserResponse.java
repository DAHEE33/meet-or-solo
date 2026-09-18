package com.survey.meetorsolo.external.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoUserResponse(
        Long id,

        @JsonProperty("kakao_account")
        KakaoAccount kakaoAccount
) {

    public String providerUserId() {
        return String.valueOf(id);
    }

    public String nickname() {
        if (kakaoAccount == null || kakaoAccount.profile == null) {
            return null;
        }
        return kakaoAccount.profile.nickname;
    }

    public String email() {
        return kakaoAccount == null ? null : kakaoAccount.email;
    }

    public String profileImageUrl() {
        if (kakaoAccount == null || kakaoAccount.profile == null) {
            return null;
        }
        return kakaoAccount.profile.profileImageUrl;
    }

    public record KakaoAccount(
            String email,
            Profile profile
    ) {
    }

    public record Profile(
            String nickname,

            @JsonProperty("profile_image_url")
            String profileImageUrl
    ) {
    }
}
