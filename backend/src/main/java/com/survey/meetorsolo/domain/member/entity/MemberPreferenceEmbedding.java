package com.survey.meetorsolo.domain.member.entity;

import com.survey.meetorsolo.global.time.SeoulDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "member_preference_embeddings")
public class MemberPreferenceEmbedding {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Column(name = "preference_text", nullable = false, columnDefinition = "TEXT")
    private String preferenceText;

    @Column(name = "embedding", columnDefinition = "vector(1536)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] embedding;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "embedding_status", nullable = false, length = 20)
    private String embeddingStatus;

    /**
     * 실패 이유({@code EmbeddingFailureReason} 이름). 실패 상태에서만 값이 있다.
     *
     * <p>회원 응답에는 내려보내지 않는다. 운영자가 로그 대신 DB에서 원인을 볼 수 있게 남기는
     * 값이다.
     */
    @Column(name = "embedding_error_reason", length = 40)
    private String embeddingErrorReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MemberPreferenceEmbedding() {
    }

    public static MemberPreferenceEmbedding create(Member member, String preferenceText) {
        MemberPreferenceEmbedding entity = new MemberPreferenceEmbedding();
        entity.member = member;
        entity.preferenceText = preferenceText;
        entity.embeddingStatus = STATUS_PENDING;
        return entity;
    }

    public void updatePreferenceText(String preferenceText) {
        this.preferenceText = preferenceText;
        this.embedding = null;
        this.embeddingModel = null;
        this.embeddingStatus = STATUS_PENDING;
        this.embeddingErrorReason = null;
    }

    public void markCompleted(float[] embedding, String model) {
        this.embedding = embedding;
        this.embeddingModel = model;
        this.embeddingStatus = STATUS_COMPLETED;
        this.embeddingErrorReason = null;
    }

    /**
     * 실패로 표시한다. 이유는 나중에 원인을 좁히기 위한 것이라 없어도 상태 전이는 그대로다.
     */
    public void markFailed(String errorReason) {
        this.embedding = null;
        this.embeddingModel = null;
        this.embeddingStatus = STATUS_FAILED;
        this.embeddingErrorReason = errorReason;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = SeoulDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = SeoulDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public String getPreferenceText() {
        return preferenceText;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public String getEmbeddingStatus() {
        return embeddingStatus;
    }

    public String getEmbeddingErrorReason() {
        return embeddingErrorReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
