package com.survey.meetorsolo.domain.member.dto;

import com.survey.meetorsolo.domain.member.entity.MemberPreferenceEmbedding;
import java.time.OffsetDateTime;

public record MemberPreferenceEmbeddingResponse(
        Long memberId,
        String preferenceText,
        String embeddingStatus,
        String embeddingModel,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static MemberPreferenceEmbeddingResponse from(MemberPreferenceEmbedding entity) {
        return new MemberPreferenceEmbeddingResponse(
                entity.getMember().getId(),
                entity.getPreferenceText(),
                entity.getEmbeddingStatus(),
                entity.getEmbeddingModel(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
