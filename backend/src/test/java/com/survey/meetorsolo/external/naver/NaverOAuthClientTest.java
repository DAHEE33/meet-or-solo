package com.survey.meetorsolo.external.naver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class NaverOAuthClientTest {

    @Test
    void authorization_url에_필수값과_state를_포함한다() {
        NaverOAuthClient client = new NaverOAuthClient(
                "client-id", "client-secret", "http://localhost:8080/api/auth/naver/callback",
                "https://nid.naver.com/oauth2.0/authorize", "https://nid.naver.com/oauth2.0/token",
                "https://openapi.naver.com/v1/nid/me", Duration.ofSeconds(1), Duration.ofSeconds(1));

        URI uri = client.buildAuthorizeUri("state-value");

        var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        assertThat(params.getFirst("response_type")).isEqualTo("code");
        assertThat(params.getFirst("client_id")).isEqualTo("client-id");
        assertThat(params.getFirst("state")).isEqualTo("state-value");
        assertThat(params.getFirst("redirect_uri"))
                .isEqualTo("http://localhost:8080/api/auth/naver/callback");
    }

    @Test
    void token_API_성공_응답을_매핑한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NaverOAuthClient client = client(builder.build());
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token?grant_type=authorization_code"
                        + "&client_id=client-id&client_secret=client-secret&code=code&state=state"))
                .andRespond(withSuccess("{\"access_token\":\"access\",\"token_type\":\"bearer\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.requestToken("code", "state").accessToken()).isEqualTo("access");
        server.verify();
    }

    @Test
    void token_API_HTTP_실패를_OAuth_오류로_변환한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NaverOAuthClient client = client(builder.build());
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token?grant_type=authorization_code"
                        + "&client_id=client-id&client_secret=client-secret&code=code&state=state"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.requestToken("code", "state"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void profile_API는_id와_null_선택값을_매핑한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NaverOAuthClient client = client(builder.build());
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andRespond(withSuccess("{\"resultcode\":\"00\",\"message\":\"success\","
                        + "\"response\":{\"id\":\"naver-id\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.requestUserInfo("access").providerUserId()).isEqualTo("naver-id");
    }

    @Test
    void profile_API에_id가_없으면_실패한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NaverOAuthClient client = client(builder.build());
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andRespond(withSuccess("{\"resultcode\":\"00\",\"response\":{}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.requestUserInfo("access")).isInstanceOf(RuntimeException.class);
    }

    private NaverOAuthClient client(RestClient restClient) {
        return new NaverOAuthClient(restClient, "client-id", "client-secret",
                "http://localhost:8080/api/auth/naver/callback",
                "https://nid.naver.com/oauth2.0/authorize", "https://nid.naver.com/oauth2.0/token",
                "https://openapi.naver.com/v1/nid/me");
    }
}
