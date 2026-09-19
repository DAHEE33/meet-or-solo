package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.config.MatchingSchedulerProperties;
import com.survey.meetorsolo.domain.matching.entity.MatchCollectionWindow;
import com.survey.meetorsolo.domain.matching.repository.MatchCollectionWindowRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭 후보 수집 구간의 수명주기를 관리한다.
 *
 * <p>구간은 "같은 축제의 첫 유효 대기자가 들어온 시점부터 일정 시간"이며, 그 시간이 끝난 뒤
 * 모인 후보를 한 번에 평가한다. tick마다 지금 대기 중인 후보 전체를 평가하면 유효한 조합이 처음
 * 생기는 순간 소진돼 후보가 2명을 넘지 못한다.
 */
@Service
public class MatchCollectionWindowService {

    private static final Logger log = LoggerFactory.getLogger(MatchCollectionWindowService.class);
    /** 생성 경쟁에서 연달아 지는 경우의 재시도 한도. 한 번만 져도 다음 회차에서 합류한다. */
    private static final int MAX_OPEN_ATTEMPTS = 3;

    private final MatchCollectionWindowRepository windows;
    private final MatchPoolRepository pools;
    private final MatchingSchedulerProperties properties;

    public MatchCollectionWindowService(
            MatchCollectionWindowRepository windows,
            MatchPoolRepository pools,
            MatchingSchedulerProperties properties
    ) {
        this.windows = windows;
        this.pools = pools;
        this.properties = properties;
    }

    /**
     * 그 축제의 열린 구간을 찾거나 새로 연다. 매칭 신청 트랜잭션 안에서 호출한다.
     *
     * <p>세 갈래다.
     *
     * <ul>
     *   <li>열린 구간이 수집 중이고 유효 후보가 남아 있으면 <b>합류</b>한다</li>
     *   <li>수집 시간이 지났으면 {@code COLLECTED}로 내리고 <b>새 구간</b>을 연다.
     *       {@code EVALUATED}가 아니라 {@code COLLECTED}인 것이 중요하다 — 평가는 아직
     *       수행되지 않았고, scheduler가 이어서 평가해야 한다</li>
     *   <li>유효 후보가 모두 빠졌으면(전원 이탈) 평가할 것이 없으므로 {@code EVALUATED}로
     *       종결하고 새 구간을 연다. 새 신청자가 온전한 수집 시간을 갖는다</li>
     * </ul>
     *
     * <p><b>이 메서드는 예외로 신청을 실패시키지 않는다.</b> 구간이 없을 때
     * {@code SELECT FOR UPDATE}는 잠글 행이 없어 동시 생성을 막지 못하므로, 생성은
     * {@code ON CONFLICT DO NOTHING}으로 시도하고 지면 재조회해 기존 구간에 합류한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MatchCollectionWindow openOrJoin(long festivalId, OffsetDateTime now) {
        Objects.requireNonNull(now, "now는 필수입니다.");
        for (int attempt = 1; attempt <= MAX_OPEN_ATTEMPTS; attempt++) {
            MatchCollectionWindow existing = windows.findOpenByFestivalIdForUpdate(festivalId).orElse(null);
            if (existing != null) {
                if (!existing.acceptsNewCandidate(now)) {
                    // 수집 시간이 끝났다. 평가 대기로 내리고 새 구간을 연다.
                    existing.markCollected(now);
                    windows.saveAndFlush(existing);
                } else if (windows.hasValidCandidates(existing.getId(), now)) {
                    return existing;
                } else {
                    // 전원 이탈. 평가할 후보가 없으므로 그대로 종결한다.
                    existing.markEvaluated(now);
                    windows.saveAndFlush(existing);
                }
            }

            OffsetDateTime endsAt = now.plus(properties.collectWindow());
            if (windows.insertOpenWindowIfAbsent(festivalId, now, endsAt, now) == 1) {
                return windows.findOpenByFestivalId(festivalId)
                        .orElseThrow(() -> new IllegalStateException("방금 생성한 수집 구간을 찾지 못했습니다."));
            }
            // 경쟁에서 졌다. 다른 트랜잭션이 만든 구간에 합류하려고 다시 돈다.
            log.debug("수집 구간 생성 경쟁에서 밀렸습니다. festivalId={}, attempt={}", festivalId, attempt);
        }
        throw new IllegalStateException(
                "수집 구간을 확보하지 못했습니다. festivalId=" + festivalId);
    }

    /**
     * 평가할 구간을 배타적으로 확보하고 소유를 표시한다.
     *
     * <p>{@code SKIP LOCKED}는 인스턴스끼리 <b>서로 다른 구간</b>을 나눠 갖게 하는 용도다. 구간을
     * 확보한 실행이 그 구간의 후보를 전량 잠그므로, 한 구간이 여러 실행으로 쪼개지지 않는다.
     *
     * <p>DB 행 잠금은 이 트랜잭션이 끝나면 풀린다. 평가가 끝날 때까지의 소유는
     * {@code EVALUATING} 상태와 {@code evaluatorToken} 임대로 표시한다. 다른 인스턴스는
     * {@code (OPEN, COLLECTED)}만 조회하므로 평가 중인 구간을 건드리지 않는다.
     */
    @Transactional
    public List<ClaimedWindow> claimEvaluableWindows(OffsetDateTime now, int limit, String evaluatorToken) {
        Objects.requireNonNull(now, "now는 필수입니다.");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
        }
        List<MatchCollectionWindow> claimed = windows.findEvaluableWindowsForUpdate(now, limit);
        claimed.forEach(window -> window.startEvaluation(evaluatorToken, now));
        return claimed.stream()
                .map(window -> new ClaimedWindow(window.getId(), window.getFestivalId()))
                .toList();
    }

    /** 확보한 구간. 잔여 후보를 다음 구간으로 옮기려면 축제도 필요하다. */
    public record ClaimedWindow(long windowId, long festivalId) {
    }

    /**
     * 평가를 종결하고 잔여 후보를 다음 구간으로 옮긴다. <b>소유한 실행만 할 수 있다.</b>
     *
     * <p>평가가 느려 stale 회수된 뒤 원래 실행이 뒤늦게 돌아오면, 그 구간은 이미 다른 실행의
     * 것일 수 있다. 토큰이 일치하지 않으면 종결도 이월도 하지 않고 {@code false}를 돌려준다.
     * 종결과 이월을 한 트랜잭션에 두어 그 사이에 소유권이 바뀌는 틈을 없앤다.
     *
     * <p>잔여 후보는 {@code entered_at}과 {@code search_expires_at}을 그대로 유지한 채 구간
     * 소속만 옮긴다. 대기 시간을 처음부터 다시 시작하지 않는다.
     *
     * @return 종결했으면 true, 소유권을 잃었으면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeEvaluation(long windowId, long festivalId, String evaluatorToken, OffsetDateTime now) {
        Objects.requireNonNull(now, "now는 필수입니다.");
        if (evaluatorToken == null || evaluatorToken.isBlank()) {
            throw new IllegalArgumentException("evaluatorToken은 필수입니다.");
        }
        if (windows.markEvaluatedIfOwned(windowId, evaluatorToken, now) == 0) {
            log.warn("소유권을 잃은 수집 구간이라 종결과 이월을 건너뜁니다. windowId={}, token={}",
                    windowId, evaluatorToken);
            return false;
        }
        if (pools.existsWaitingInWindow(windowId, now)) {
            long nextWindowId = openOrJoinInNewTransaction(festivalId, now).getId();
            int movedCount = pools.carryOverToNextWindow(windowId, nextWindowId, now);
            if (movedCount > 0) {
                log.debug("잔여 후보를 다음 수집 구간으로 옮겼습니다. from={}, to={}, count={}",
                        windowId, nextWindowId, movedCount);
            }
        }
        return true;
    }

    /**
     * 평가 도중 장애로 {@code EVALUATING}에 남은 구간을 평가 대기로 되돌린다.
     *
     * <p>되돌린 뒤 다시 평가해도 중복 제안이 생기지 않는다. 제안이 만들어진 회원의 pool은 같은
     * 트랜잭션에서 {@code PROPOSED}가 되어 커밋됐고, 후보 조회 조건이 {@code WAITING}이라
     * 재평가 대상에서 빠진다.
     */
    @Transactional
    public int releaseStaleEvaluations(OffsetDateTime now, OffsetDateTime staleBefore) {
        Objects.requireNonNull(now, "now는 필수입니다.");
        Objects.requireNonNull(staleBefore, "staleBefore는 필수입니다.");
        int released = windows.releaseStaleEvaluatingWindows(now, staleBefore);
        if (released > 0) {
            log.info("정체된 수집 구간 평가를 회수했습니다. count={}", released);
        }
        return released;
    }

    /**
     * 평가 후 남은 후보를 담을 다음 구간을 준비한다.
     *
     * <p>잔여 후보는 {@code entered_at}과 {@code search_expires_at}을 그대로 유지한 채 구간 소속만
     * 옮긴다. 대기 시간을 처음부터 다시 시작하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MatchCollectionWindow nextWindow(long festivalId, OffsetDateTime now) {
        return openOrJoinInNewTransaction(festivalId, now);
    }

    private MatchCollectionWindow openOrJoinInNewTransaction(long festivalId, OffsetDateTime now) {
        for (int attempt = 1; attempt <= MAX_OPEN_ATTEMPTS; attempt++) {
            MatchCollectionWindow existing = windows.findOpenByFestivalIdForUpdate(festivalId).orElse(null);
            if (existing != null && existing.acceptsNewCandidate(now)) {
                return existing;
            }
            if (existing != null) {
                existing.markCollected(now);
                windows.saveAndFlush(existing);
            }
            OffsetDateTime endsAt = now.plus(properties.collectWindow());
            if (windows.insertOpenWindowIfAbsent(festivalId, now, endsAt, now) == 1) {
                return windows.findOpenByFestivalId(festivalId)
                        .orElseThrow(() -> new IllegalStateException("방금 생성한 수집 구간을 찾지 못했습니다."));
            }
        }
        throw new IllegalStateException("다음 수집 구간을 확보하지 못했습니다. festivalId=" + festivalId);
    }
}
