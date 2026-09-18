package com.survey.meetorsolo.external.kakao;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

class KakaoOAuthClientTest {

    @Test
    void authorization_url에_필수값과_state와_계정_선택_prompt를_포함한다() {
        KakaoOAuthClient client = new KakaoOAuthClient(
                "client-id", "client-secret", "http://localhost:8080/api/auth/kakao/callback");

        URI uri = client.buildAuthorizeUri("state-value");

        var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        assertThat(uri.toString()).startsWith("https://kauth.kakao.com/oauth/authorize");
        assertThat(params.getFirst("response_type")).isEqualTo("code");
        assertThat(params.getFirst("client_id")).isEqualTo("client-id");
        assertThat(params.getFirst("state")).isEqualTo("state-value");
        assertThat(params.getFirst("redirect_uri"))
                .isEqualTo("http://localhost:8080/api/auth/kakao/callback");
        // 우리 로그아웃은 카카오 계정 세션을 끊지 못한다. 이 값이 없으면 직전 계정으로 즉시
        // 재로그인되어 다른 계정으로 바꿀 수 없다.
        assertThat(params.getFirst("prompt")).isEqualTo("select_account");
    }
}
