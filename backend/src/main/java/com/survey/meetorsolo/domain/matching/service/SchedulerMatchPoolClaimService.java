package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulerMatchPoolClaimService {
    private static final int MAX_LOCK_TOKEN_LENGTH = 100;
    private final MatchPoolRepository matchPoolRepository;
    public SchedulerMatchPoolClaimService(MatchPoolRepository matchPoolRepository) {
        this.matchPoolRepository = matchPoolRepository;
    }
    /**
     * 한 수집 구간의 유효 후보를 <b>전량</b> 선점한다.
     *
     * <p>LIMIT을 걸지 않는다. 구간 후보를 신청순으로 자르면 뒤쪽 후보가 비교 대상에서 빠진다.
     * {@code SKIP LOCKED}도 쓰지 않는다. 구간은 이미 배타적으로 확보한 뒤이고, 여기서 건너뛰면
     * 같은 구간의 후보가 쪼개져 서로 비교되지 않는다.
     */
    @Transactional
    public MatchPoolClaimResult claimWindow(long windowId, OffsetDateTime now, String lockToken) {
        Objects.requireNonNull(now, "now는 필수입니다.");
        if (windowId <= 0) throw new IllegalArgumentException("windowId는 양수여야 합니다.");
        if (lockToken == null || lockToken.isBlank()) throw new IllegalArgumentException("lockToken은 필수입니다.");
        if (lockToken.length() > MAX_LOCK_TOKEN_LENGTH) throw new IllegalArgumentException("lockToken은 100자 이하여야 합니다.");
        List<MatchPool> pools = matchPoolRepository.findWindowCandidatesForUpdate(windowId, now);
        pools.forEach(pool -> pool.lock(now, lockToken));
        return new MatchPoolClaimResult(lockToken, pools.stream().map(MatchPool::getId).toList());
    }
}
