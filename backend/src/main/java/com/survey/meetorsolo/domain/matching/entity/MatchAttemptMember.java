package com.survey.meetorsolo.domain.matching.entity;

import com.survey.meetorsolo.domain.matching.scoring.MemberScoreBreakdown;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "match_attempt_members")
public class MatchAttemptMember {
    public static final String STATUS_PROPOSED = "PROPOSED";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_EXCLUDED = "EXCLUDED";
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "attempt_id", nullable = false) private Long attemptId;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "pool_id", nullable = false) private Long poolId;
    @Column(name = "member_score", nullable = false, precision = 10, scale = 2) private BigDecimal memberScore;
    /** 태그 Jaccard 평균. 점수 분해 저장(V24) 이전 row는 null이다. */
    @Column(name = "jaccard_score", precision = 10, scale = 2) private BigDecimal jaccardScore;
    /** 임베딩 항 투입값 평균. 임베딩 pair가 없으면 null이고, V24 이전 row도 null이다. */
    @Column(name = "cosine_score", precision = 10, scale = 2) private BigDecimal cosineScore;
    /** 임베딩 pair가 1개 이상인지 여부. V24 이전 row는 null이다. */
    @Column(name = "embedding_applied") private Boolean embeddingApplied;
    /** 실제 임베딩이 적용된 pair 수. V24 이전 row는 null이다. */
    @Column(name = "embedding_pair_count") private Short embeddingPairCount;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    protected MatchAttemptMember() { }
    /**
     * 제안 시점의 회원 점수와 그 분해값을 함께 확정 저장한다.
     *
     * <p>{@code member_score}는 {@code breakdown.total()}이며 분해 저장 도입 전과 같은 값이다.
     * 분해값은 "임베딩이 실제로 매칭 품질을 높였는가"를 사후에 판단하기 위한 것으로, 총점만 남기면
     * 그 시점의 태그·벡터·가중치를 모두 다시 모아야 역산이 가능하고 시간이 지나면 불가능해진다.
     */
    public static MatchAttemptMember proposed(long attemptId, long memberId, long poolId,
                                               MemberScoreBreakdown breakdown, OffsetDateTime now) {
        Objects.requireNonNull(breakdown, "breakdown은 필수입니다.");
        MatchAttemptMember member = new MatchAttemptMember();
        member.attemptId = attemptId; member.memberId = memberId; member.poolId = poolId;
        member.memberScore = breakdown.total(); member.status = STATUS_PROPOSED;
        member.jaccardScore = breakdown.jaccard();
        member.cosineScore = breakdown.cosine();
        member.embeddingApplied = breakdown.embeddingApplied();
        member.embeddingPairCount = (short) breakdown.embeddingPairCount();
        member.createdAt = now; member.updatedAt = now;
        return member;
    }
    public Long getId() { return id; }
    public Long getAttemptId() { return attemptId; }
    public Long getMemberId() { return memberId; }
    public Long getPoolId() { return poolId; }
    public String getStatus() { return status; }
    public BigDecimal getMemberScore() { return memberScore; }
    public BigDecimal getJaccardScore() { return jaccardScore; }
    public BigDecimal getCosineScore() { return cosineScore; }
    public Boolean getEmbeddingApplied() { return embeddingApplied; }
    public Short getEmbeddingPairCount() { return embeddingPairCount; }
    public void respond(String response, OffsetDateTime now) {
        if (!STATUS_PROPOSED.equals(status)) throw new IllegalStateException("PROPOSED member만 응답할 수 있습니다.");
        status = response; updatedAt = now;
    }
    public void exclude(OffsetDateTime now) {
        if (STATUS_PROPOSED.equals(status)) { status = STATUS_EXCLUDED; updatedAt = now; }
    }
}
