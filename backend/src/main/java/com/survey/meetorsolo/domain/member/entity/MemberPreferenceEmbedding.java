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
    }

    public void markCompleted(float[] embedding, String model) {
        this.embedding = embedding;
        this.embeddingModel = model;
        this.embeddingStatus = STATUS_COMPLETED;
    }

    public void markFailed() {
        this.embedding = null;
        this.embeddingModel = null;
        this.embeddingStatus = STATUS_FAILED;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
