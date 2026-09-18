package com.survey.meetorsolo.external.openai.dto;

import java.util.List;

public record OpenAiEmbeddingResponse(
        List<EmbeddingData> data,
        String model
) {
    public record EmbeddingData(int index, List<Float> embedding) {
    }
}
