package com.survey.meetorsolo.external.kakao;

import com.survey.meetorsolo.external.kakao.dto.KakaoTokenResponse;
import com.survey.meetorsolo.external.kakao.dto.KakaoUserResponse;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KakaoOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoOAuthClient.class);

    private static final String KAKAO_AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public KakaoOAuthClient(
            @Value("${app.oauth.kakao.client-id}") String clientId,
            @Value("${app.oauth.kakao.client-secret}") String clientSecret,
            @Value("${app.oauth.kakao.redirect-uri}") String redirectUri
    ) {
        this.restClient = RestClient.create();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public URI buildAuthorizeUri(String state) {
        validateRequired("KAKAO_CLIENT_ID", clientId);
        validateRequired("KAKAO_REDIRECT_URI", redirectUri);
        return UriComponentsBuilder.fromHttpUrl(KAKAO_AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .build()
                .toUri();
    }

    public KakaoTokenResponse requestToken(String code) {
        validateRequired("KAKAO_CLIENT_ID", clientId);
        validateRequired("KAKAO_CLIENT_SECRET", clientSecret);
        validateRequired("KAKAO_REDIRECT_URI", redirectUri);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("code", code);

        try {
            KakaoTokenResponse response = restClient.post()
                    .uri(KAKAO_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
            }
            return response;
        } catch (RestClientResponseException exception) {
            log.warn(
                    "Kakao token API request failed. status={}, body={}, redirect_uri={}, client_id={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    redirectUri,
                    maskClientId(clientId)
            );
            throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
        }
    }

    public KakaoUserResponse requestUserInfo(String accessToken) {
        try {
            KakaoUserResponse response = restClient.get()
                    .uri(KAKAO_USER_INFO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
            if (response == null || response.id() == null) {
                throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
            }
            return response;
        } catch (RestClientResponseException exception) {
            log.warn(
                    "Kakao user info API request failed. status={}, body={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString()
            );
            throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
        }
    }

    private void validateRequired(String envName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(envName + " 환경변수가 필요합니다.");
        }
    }

    private String maskClientId(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 6) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 6) + "*".repeat(value.length() - 6);
    }
}
