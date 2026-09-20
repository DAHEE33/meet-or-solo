package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchCollectionWindow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchCollectionWindowRepository extends JpaRepository<MatchCollectionWindow, Long> {

    /**
     * 그 축제의 열린 구간. 신청 경로에서 합류 대상을 찾을 때 쓴다.
     *
     * <p>{@code FOR UPDATE}는 이미 존재하는 행만 잠근다. 행이 없을 때의 동시 생성은 이 잠금으로
     * 막히지 않으므로 {@link #insertOpenWindowIfAbsent}의 {@code ON CONFLICT}가 함께 필요하다.
     */
    @Query(value = """
            SELECT * FROM match_collection_windows
            WHERE festival_id = :festivalId AND status = 'OPEN'
            FOR UPDATE
            """, nativeQuery = true)
    Optional<MatchCollectionWindow> findOpenByFestivalIdForUpdate(@Param("festivalId") long festivalId);

    /** 잠금 없이 열린 구간만 확인한다. 재조회 경로에서 쓴다. */
    @Query(value = """
            SELECT * FROM match_collection_windows
            WHERE festival_id = :festivalId AND status = 'OPEN'
            """, nativeQuery = true)
    Optional<MatchCollectionWindow> findOpenByFestivalId(@Param("festivalId") long festivalId);

    /**
     * 열린 구간이 없을 때만 새로 만든다.
     *
     * <p>부분 unique 인덱스({@code uq_match_collection_windows_open_festival})와 짝이다. 두 신청이
     * 동시에 "열린 구간 없음"을 보고 들어와도 한쪽만 성공하고, 진 쪽은 예외 대신 0을 돌려받아
     * 재조회로 기존 구간에 합류한다. <b>매칭 신청이 unique 위반으로 실패하지 않게 하는 것이
     * 이 쿼리의 목적이다.</b>
     *
     * <p>{@code ON CONFLICT DO NOTHING}은 경쟁 트랜잭션이 커밋될 때까지 대기한 뒤 0행을 반환하고,
     * READ COMMITTED에서 이어지는 재조회는 커밋된 행을 본다.
     *
     * @return 생성했으면 1, 이미 있어서 건너뛰었으면 0
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO match_collection_windows
                (festival_id, started_at, ends_at, status, created_at, updated_at)
            VALUES (:festivalId, :startedAt, :endsAt, 'OPEN', :now, :now)
            ON CONFLICT (festival_id) WHERE status = 'OPEN' DO NOTHING
            """, nativeQuery = true)
    int insertOpenWindowIfAbsent(
            @Param("festivalId") long festivalId,
            @Param("startedAt") OffsetDateTime startedAt,
            @Param("endsAt") OffsetDateTime endsAt,
            @Param("now") OffsetDateTime now
    );

    /**
     * 평가해야 할 구간을 배타적으로 확보한다.
     *
     * <p><b>{@code OPEN}과 {@code COLLECTED}를 함께 본다.</b> 신규 신청이 만료된 구간을 먼저
     * {@code COLLECTED}로 내렸더라도 평가 대상에서 빠지지 않아야 한다.
     *
     * <p>{@code SKIP LOCKED}는 여기서만 쓴다. 인스턴스끼리 서로 <b>다른 구간</b>을 나눠 갖게 하는
     * 용도이며, 한 구간 안의 후보를 나눠 갖게 하려는 것이 아니다. 구간을 확보한 실행이 그 구간의
     * 후보를 전량 잠근다.
     */
    @Query(value = """
            SELECT * FROM match_collection_windows
            WHERE status IN ('OPEN', 'COLLECTED')
              AND ends_at <= :now
            ORDER BY ends_at ASC, id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<MatchCollectionWindow> findEvaluableWindowsForUpdate(
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit
    );

    /**
     * 평가 도중 장애로 {@code EVALUATING}에 남은 구간을 평가 대기로 되돌린다.
     *
     * <p>되돌린 뒤 다시 평가해도 중복 제안이 생기지 않는다. 제안이 만들어진 회원의 pool은 같은
     * 트랜잭션에서 {@code PROPOSED}가 되어 커밋되고, 후보 조회 조건이 {@code WAITING}이라
     * 재평가 대상에서 빠지기 때문이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE match_collection_windows
            SET status = 'COLLECTED', evaluator_token = NULL, evaluation_started_at = NULL,
                updated_at = :now
            WHERE status = 'EVALUATING'
              AND evaluation_started_at IS NOT NULL
              AND evaluation_started_at <= :staleBefore
            """, nativeQuery = true)
    int releaseStaleEvaluatingWindows(
            @Param("now") OffsetDateTime now,
            @Param("staleBefore") OffsetDateTime staleBefore
    );

    /**
     * 소유한 실행만 구간을 종결할 수 있다.
     *
     * <p>평가가 느려 stale 회수된 뒤 원래 실행이 뒤늦게 돌아오는 경우가 있다. 그때는 이미 다른
     * 실행이 구간을 가져갔을 수 있으므로, 토큰이 일치할 때만 종결한다. 일치하지 않으면 0을
     * 돌려주고 호출자는 잔여 후보 이월도 하지 않는다.
     *
     * @return 종결했으면 1, 소유권을 잃었으면 0
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE match_collection_windows
            SET status = 'EVALUATED', evaluator_token = NULL, evaluation_started_at = NULL,
                updated_at = :now
            WHERE id = :windowId
              AND status = 'EVALUATING'
              AND evaluator_token = :evaluatorToken
            """, nativeQuery = true)
    int markEvaluatedIfOwned(
            @Param("windowId") long windowId,
            @Param("evaluatorToken") String evaluatorToken,
            @Param("now") OffsetDateTime now
    );

    /** 그 구간에 아직 유효한 대기 후보가 있는지. 전원 이탈 판정에 쓴다. */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM match_pools pool
                JOIN festival_checkins checkin ON checkin.id = pool.checkin_id
                WHERE pool.collect_window_id = :windowId
                  AND pool.status = 'WAITING'
                  AND pool.search_expires_at > :now
                  AND checkin.member_id = pool.member_id
                  AND checkin.festival_id = pool.festival_id
                  AND checkin.status = 'ACTIVE'
                  AND LEAST(checkin.expires_at, checkin.checked_in_at + INTERVAL '1 hour') > :now
            )
            """, nativeQuery = true)
    boolean hasValidCandidates(@Param("windowId") long windowId, @Param("now") OffsetDateTime now);
}
