package com.survey.meetorsolo.external.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OpenAiEmbeddingConfigTest {

    private static final String MODEL = "text-embedding-3-small";

    private final OpenAiEmbeddingConfig config = new OpenAiEmbeddingConfig();

    @Test
    void API_key가_비어_있으면_키_없음_상태로_client를_만든다() {
        // 기동은 실패시키지 않는다. 임베딩 실패가 서비스를 막지 않는다는 원칙 때문이다.
        assertThat(config.openAiEmbeddingClient(properties(null)).isApiKeyPresent()).isFalse();
        assertThat(config.openAiEmbeddingClient(properties("   ")).isApiKeyPresent()).isFalse();
    }

    @Test
    void 앞뒤_공백과_줄바꿈이_붙은_key도_사용한다() {
        // Windows에서 편집한 .env를 서버로 옮기면 값 끝에 CR이 붙는다. 그대로 두면
        // "Bearer sk-...\r"가 되어 HTTP client가 헤더 자체를 거절한다.
        OpenAiEmbeddingClient client = config.openAiEmbeddingClient(properties("  sk-test-key\r\n"));

        assertThat(client.isApiKeyPresent()).isTrue();
    }

    @Test
    void 형식이_달라도_주입된_key는_그대로_쓴다() {
        // 경고만 남기고 막지 않는다. key 형식은 발급처 사정으로 바뀔 수 있다.
        assertThat(config.openAiEmbeddingClient(properties("custom-key")).isApiKeyPresent()).isTrue();
    }

    @Test
    void 모델_이름을_그대로_전달한다() {
        assertThat(config.openAiEmbeddingClient(properties("sk-test-key")).getModel()).isEqualTo(MODEL);
    }

    private OpenAiProperties properties(String apiKey) {
        return new OpenAiProperties(
                apiKey, "https://api.openai.com", MODEL, Duration.ofSeconds(5), Duration.ofSeconds(10));
    }
}
