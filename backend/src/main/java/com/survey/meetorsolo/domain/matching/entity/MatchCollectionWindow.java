package com.survey.meetorsolo.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 매칭 후보 수집 구간.
 *
 * <p>같은 축제의 첫 유효 대기자가 들어온 시점부터 일정 시간을 하나의 구간으로 묶고, 그 구간이
 * 끝난 뒤 모인 후보를 한 번에 평가한다. tick마다 "지금 대기 중인 후보 전체"를 평가하면 유효한
 * 조합이 처음 생기는 순간 소진돼 후보가 2명을 넘지 못하고, 조합이 1개면 궁합 점수가 순위에
 * 개입할 수 없다.
 *
 * <p><b>{@code endsAt}은 생성 후 바꾸지 않는다.</b> 후보의 {@code MIN(entered_at)}으로 구간을
 * 유도하면 첫 신청자가 취소·매칭·만료될 때 종료 시각이 움직인다.
 *
 * <p><b>수집 종료와 평가 완료는 다른 사건이다.</b> 둘을 한 상태로 합치면, 신규 신청이 만료된
 * 구간을 닫았을 때 scheduler가 그 구간을 조회하지 않아 평가가 누락된다. 그래서 신청 경로는
 * {@link #STATUS_COLLECTED}까지만 내리고, 평가는 scheduler가 별도로 수행한다.
 */
@Entity
@Table(name = "match_collection_windows")
public class MatchCollectionWindow {

    /** 수집 중. 축제당 이 상태의 행은 최대 하나다(부분 unique 인덱스). */
    public static final String STATUS_OPEN = "OPEN";
    /** 수집 종료, 평가 대기. 신청 경로가 내릴 수 있는 마지막 상태다. */
    public static final String STATUS_COLLECTED = "COLLECTED";
    /** 한 실행이 소유하고 평가 중. */
    public static final String STATUS_EVALUATING = "EVALUATING";
    /** 평가 완료 또는 유효 후보 0건으로 종결. */
    public static final String STATUS_EVALUATED = "EVALUATED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "festival_id", nullable = false)
    private Long festivalId;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Column(nullable = false, length = 20)
    private String status;

    /**
     * 평가를 소유한 실행의 토큰.
     *
     * <p>DB 행 잠금은 claim 트랜잭션이 끝나면 풀리므로 평가가 끝날 때까지의 소유를 보장하지
     * 못한다. {@code match_pools.lock_token}과 같은 방식으로 토큰 임대를 쓴다.
     */
    @Column(name = "evaluator_token", length = 100)
    private String evaluatorToken;

    @Column(name = "evaluation_started_at")
    private OffsetDateTime evaluationStartedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MatchCollectionWindow() {
    }

    public static MatchCollectionWindow open(long festivalId, OffsetDateTime startedAt, Duration window) {
        Objects.requireNonNull(startedAt, "startedAt은 필수입니다.");
        Objects.requireNonNull(window, "window는 필수입니다.");
        if (festivalId <= 0) {
            throw new IllegalArgumentException("festivalId는 양수여야 합니다.");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window는 양수여야 합니다.");
        }
        MatchCollectionWindow entity = new MatchCollectionWindow();
        entity.festivalId = festivalId;
        entity.startedAt = startedAt;
        entity.endsAt = startedAt.plus(window);
        entity.status = STATUS_OPEN;
        entity.createdAt = startedAt;
        entity.updatedAt = startedAt;
        return entity;
    }

    /** 수집 시간이 끝났는지. 경계값은 종료로 본다. */
    public boolean collectionEnded(OffsetDateTime now) {
        return !now.isBefore(endsAt);
    }

    /** 이 구간에 새 후보를 받을 수 있는지. */
    public boolean acceptsNewCandidate(OffsetDateTime now) {
        return STATUS_OPEN.equals(status) && !collectionEnded(now);
    }

    /**
     * 수집을 종료한다. 평가는 아직 하지 않는다.
     *
     * <p>신청 경로가 만료된 구간을 발견했을 때도 이 상태까지만 내린다. scheduler가
     * {@code (OPEN, COLLECTED)}를 함께 조회하므로 평가가 누락되지 않는다.
     */
    public void markCollected(OffsetDateTime now) {
        if (!STATUS_OPEN.equals(status)) {
            return;
        }
        this.status = STATUS_COLLECTED;
        this.updatedAt = now;
    }

    /** 평가 소유권을 잡는다. 토큰은 같은 tick의 {@code match_pools.lock_token}과 같은 값을 쓴다. */
    public void startEvaluation(String evaluatorToken, OffsetDateTime now) {
        if (evaluatorToken == null || evaluatorToken.isBlank()) {
            throw new IllegalArgumentException("evaluatorToken은 필수입니다.");
        }
        this.status = STATUS_EVALUATING;
        this.evaluatorToken = evaluatorToken;
        this.evaluationStartedAt = now;
        this.updatedAt = now;
    }

    /** 평가를 끝낸다. 유효 후보가 0건이라 평가할 것이 없었던 경우에도 같은 상태로 종결한다. */
    public void markEvaluated(OffsetDateTime now) {
        this.status = STATUS_EVALUATED;
        this.evaluatorToken = null;
        this.evaluationStartedAt = null;
        this.updatedAt = now;
    }

    /** 평가 도중 장애로 남은 구간을 평가 대기로 되돌린다. */
    public void releaseEvaluation(OffsetDateTime now) {
        this.status = STATUS_COLLECTED;
        this.evaluatorToken = null;
        this.evaluationStartedAt = null;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getFestivalId() {
        return festivalId;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public String getStatus() {
        return status;
    }

    public String getEvaluatorToken() {
        return evaluatorToken;
    }

    public OffsetDateTime getEvaluationStartedAt() {
        return evaluationStartedAt;
    }
}
