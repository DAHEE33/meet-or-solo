package com.survey.meetorsolo.external.naver;

import com.survey.meetorsolo.external.naver.dto.NaverTokenResponse;
import com.survey.meetorsolo.external.naver.dto.NaverUserResponse;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class NaverOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(NaverOAuthClient.class);
    private static final String SUCCESS_RESULT_CODE = "00";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String authorizationUri;
    private final String tokenUri;
    private final String userInfoUri;

    @Autowired
    public NaverOAuthClient(
            @Value("${app.oauth.naver.client-id}") String clientId,
            @Value("${app.oauth.naver.client-secret}") String clientSecret,
            @Value("${app.oauth.naver.redirect-uri}") String redirectUri,
            @Value("${app.oauth.naver.authorization-uri}") String authorizationUri,
            @Value("${app.oauth.naver.token-uri}") String tokenUri,
            @Value("${app.oauth.naver.user-info-uri}") String userInfoUri,
            @Value("${app.oauth.naver.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.oauth.naver.read-timeout:5s}") Duration readTimeout
    ) {
        this(createRestClient(connectTimeout, readTimeout), clientId, clientSecret, redirectUri,
                authorizationUri, tokenUri, userInfoUri);
    }

    NaverOAuthClient(
            RestClient restClient,
            String clientId,
            String clientSecret,
            String redirectUri,
            String authorizationUri,
            String tokenUri,
            String userInfoUri
    ) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.authorizationUri = authorizationUri;
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
    }

    private static RestClient createRestClient(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    public URI buildAuthorizeUri(String state) {
        validateRequired("NAVER_CLIENT_ID", clientId);
        validateRequired("NAVER_REDIRECT_URI", redirectUri);
        if (state == null || state.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return UriComponentsBuilder.fromUriString(authorizationUri)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .build()
                .encode()
                .toUri();
    }

    public NaverTokenResponse requestToken(String code, String state) {
        validateRequired("NAVER_CLIENT_ID", clientId);
        validateRequired("NAVER_CLIENT_SECRET", clientSecret);
        try {
            NaverTokenResponse response = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(tokenUri)
                            .queryParam("grant_type", "authorization_code")
                            .queryParam("client_id", clientId)
                            .queryParam("client_secret", clientSecret)
                            .queryParam("code", code)
                            .queryParam("state", state)
                            .build()
                            .encode()
                            .toUri())
                    .retrieve()
                    .body(NaverTokenResponse.class);
            if (response == null || response.error() != null
                    || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
            }
            return response;
        } catch (RestClientResponseException exception) {
            log.warn("Naver OAuth API failed. endpoint=token, status={}", exception.getStatusCode());
            throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
        } catch (RestClientException exception) {
            log.warn("Naver OAuth API failed. endpoint=token, cause={}", exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
        }
    }

    public NaverUserResponse requestUserInfo(String accessToken) {
        try {
            NaverUserResponse response = restClient.get()
                    .uri(userInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(NaverUserResponse.class);
            if (response == null || !SUCCESS_RESULT_CODE.equals(response.resultcode())
                    || response.providerUserId() == null || response.providerUserId().isBlank()) {
                throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
            }
            return response;
        } catch (RestClientResponseException exception) {
            log.warn("Naver OAuth API failed. endpoint=profile, status={}", exception.getStatusCode());
            throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
        } catch (RestClientException exception) {
            log.warn("Naver OAuth API failed. endpoint=profile, cause={}", exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
        }
    }

    private void validateRequired(String envName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(envName + " 환경변수가 필요합니다.");
        }
    }
}
