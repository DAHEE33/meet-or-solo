package com.survey.meetorsolo.domain.matching.group;

import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record MatchingCandidate(
        long poolId,
        long memberId,
        long checkinId,
        long festivalId,
        int preferredGroupSize,
        boolean allowMinimumTwo,
        OffsetDateTime enteredAt,
        List<TravelStyleCode> travelStyles,
        float[] preferenceEmbedding
) {

    /** 임베딩 없이 생성하는 8인자 편의 생성자. */
    public MatchingCandidate(
            long poolId,
            long memberId,
            long checkinId,
            long festivalId,
            int preferredGroupSize,
            boolean allowMinimumTwo,
            OffsetDateTime enteredAt,
            Collection<TravelStyleCode> travelStyles
    ) {
        this(poolId, memberId, checkinId, festivalId, preferredGroupSize, allowMinimumTwo,
                enteredAt, travelStyles, null);
    }

    /** 임베딩 포함 9인자 생성자. preferenceEmbedding은 null 허용(임베딩 미생성 회원). */
    public MatchingCandidate(
            long poolId,
            long memberId,
            long checkinId,
            long festivalId,
            int preferredGroupSize,
            boolean allowMinimumTwo,
            OffsetDateTime enteredAt,
            Collection<TravelStyleCode> travelStyles,
            float[] preferenceEmbedding
    ) {
        this(
                poolId,
                memberId,
                checkinId,
                festivalId,
                preferredGroupSize,
                allowMinimumTwo,
                Objects.requireNonNull(enteredAt, "enteredAt은 필수입니다."),
                List.copyOf(Objects.requireNonNull(travelStyles, "travelStyles는 필수입니다.")),
                preferenceEmbedding == null ? null : preferenceEmbedding.clone()
        );
        if (poolId <= 0) {
            throw new IllegalArgumentException("poolId는 양수여야 합니다.");
        }
        if (memberId <= 0) {
            throw new IllegalArgumentException("memberId는 양수여야 합니다.");
        }
        if (checkinId <= 0) {
            throw new IllegalArgumentException("checkinId는 양수여야 합니다.");
        }
        if (festivalId <= 0) {
            throw new IllegalArgumentException("festivalId는 양수여야 합니다.");
        }
        if (preferredGroupSize < 2 || preferredGroupSize > 4) {
            throw new IllegalArgumentException("preferredGroupSize는 2, 3, 4 중 하나여야 합니다.");
        }
        if (travelStyles.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("travelStyles에는 null을 포함할 수 없습니다.");
        }
    }
}
