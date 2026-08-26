package com.survey.meetorsolo.external.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.survey.meetorsolo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(builder.build(), MODEL);

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
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(builder.build(), MODEL);

        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.embed("테스트"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 빈_data_응답을_BusinessException으로_변환한다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(builder.build(), MODEL);

        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andRespond(withSuccess(
                        "{\"data\":[],\"model\":\"" + MODEL + "\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed("테스트"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 빈_텍스트를_거부한다() {
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(
                RestClient.builder().build(), MODEL);

        assertThatThrownBy(() -> client.embed(""))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.embed(null))
                .isInstanceOf(BusinessException.class);
    }
}
