package com.survey.meetorsolo.external.naver.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NaverUserResponseTest {

    @Test
    void 선택_프로필이_null이어도_안전하게_매핑한다() {
        NaverUserResponse response = new NaverUserResponse(
                "00", "success", new NaverUserResponse.Profile("id", null, null, null, null, null, null, null));

        assertThat(response.providerUserId()).isEqualTo("id");
        assertThat(response.email()).isNull();
        assertThat(response.nickname()).isNull();
        assertThat(response.profileImageUrl()).isNull();
        assertThat(response.gender()).isNull();
        assertThat(response.age()).isNull();
        assertThat(response.birthyear()).isNull();
    }
}
