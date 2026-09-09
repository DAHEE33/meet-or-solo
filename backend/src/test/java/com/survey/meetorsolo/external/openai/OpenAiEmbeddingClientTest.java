package com.survey.meetorsolo.external.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.survey.meetorsolo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiEmbeddingClientTest {

    private static final String MODEL = "text-embedding-3-small";

    @Test
    void 성공_응답에서_1536차원_임베딩을_추출한다() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.openai.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(builder.build(), MODEL, true);

        StringBuilder embeddingJson = new StringBuilder("[");
        for (int i = 0; i < 1536; i++) {
            if (i > 0) embeddingJson.append(",");
            embeddingJson.append("0.01");
        }
        embeddingJson.append("]");

        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"data\":[{\"index\":0,\"embedding\":" + embeddingJson + "}],\"model\":\"" + MODEL + "\"}",
                        MediaType.APPLICATION_JSON));

        float[] result = client.embed("축제에서 맛집을 탐방하고 싶어요");

        assertThat(result).hasSize(1536);
        assertThat(result[0]).isEqualTo(0.01f);
        server.verify();
    }

    @Test
    void HTTP_오류_응답을_BusinessException으로_변환한다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(builder.build(), MODEL, true);

        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.embed("테스트"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 빈_data_응답을_BusinessException으로_변환한다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(builder.build(), MODEL, true);

        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andRespond(withSuccess(
                        "{\"data\":[],\"model\":\"" + MODEL + "\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed("테스트"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 상태코드별로_실패_이유를_구분한다() {
        // 로컬은 되는데 서버만 실패할 때, 키 문제(401/403)와 사용량(429)과 상대 장애(5xx)를
        // 로그·DB에서 갈라 보려면 상태코드를 이유로 옮겨 놓아야 한다.
        assertThat(reasonOf(HttpStatus.UNAUTHORIZED)).isEqualTo(EmbeddingFailureReason.UNAUTHORIZED);
        assertThat(reasonOf(HttpStatus.FORBIDDEN)).isEqualTo(EmbeddingFailureReason.UNAUTHORIZED);
        assertThat(reasonOf(HttpStatus.TOO_MANY_REQUESTS)).isEqualTo(EmbeddingFailureReason.RATE_LIMITED);
        assertThat(reasonOf(HttpStatus.BAD_REQUEST)).isEqualTo(EmbeddingFailureReason.INVALID_REQUEST);
        assertThat(reasonOf(HttpStatus.INTERNAL_SERVER_ERROR)).isEqualTo(EmbeddingFailureReason.UPSTREAM_ERROR);
    }

    @Test
    void 응답_형식이_어긋나면_INVALID_RESPONSE로_남긴다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(builder.build(), MODEL, true);

        // 차원이 1536이 아니면 저장해도 매칭에서 쓸 수 없다.
        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andRespond(withSuccess(
                        "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}],\"model\":\"" + MODEL + "\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed("테스트"))
                .isInstanceOf(EmbeddingFailedException.class)
                .extracting(exception -> ((EmbeddingFailedException) exception).getReason())
                .isEqualTo(EmbeddingFailureReason.INVALID_RESPONSE);
    }

    @Test
    void API_key가_없으면_호출하지_않고_API_KEY_MISSING으로_실패한다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(builder.build(), MODEL, false);

        assertThatThrownBy(() -> client.embed("테스트"))
                .isInstanceOf(EmbeddingFailedException.class)
                .extracting(exception -> ((EmbeddingFailedException) exception).getReason())
                .isEqualTo(EmbeddingFailureReason.API_KEY_MISSING);
        // 요청을 아예 보내지 않는다. 보내봐야 401이고, 이유가 키 문제로 안 남는다.
        server.verify();
    }

    private EmbeddingFailureReason reasonOf(HttpStatus status) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(builder.build(), MODEL, true);

        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andRespond(withStatus(status));

        try {
            client.embed("테스트");
            throw new AssertionError("실패해야 한다");
        } catch (EmbeddingFailedException exception) {
            return exception.getReason();
        }
    }

    @Test
    void 빈_텍스트를_거부한다() {
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(
                RestClient.builder().build(), MODEL, true);

        assertThatThrownBy(() -> client.embed(""))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.embed(null))
                .isInstanceOf(BusinessException.class);
    }
}
