package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PoolEntryMatchPoolClaimService {

    private static final int MAX_LOCK_TOKEN_LENGTH = 100;

    private final MatchPoolRepository matchPoolRepository;

    public PoolEntryMatchPoolClaimService(MatchPoolRepository matchPoolRepository) {
        this.matchPoolRepository = matchPoolRepository;
    }

    @Transactional
    public MatchPoolClaimResult claim(
            long requesterPoolId,
            long requesterMemberId,
            long festivalId,
            OffsetDateTime now,
            int limit,
            String lockToken
    ) {
        Objects.requireNonNull(now, "now는 필수입니다.");
        if (requesterPoolId <= 0 || requesterMemberId <= 0 || festivalId <= 0) {
            throw new IllegalArgumentException("poolId, memberId, festivalId는 양수여야 합니다.");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
        }
        if (lockToken == null || lockToken.isBlank()) {
            throw new IllegalArgumentException("lockToken은 필수입니다.");
        }
        if (lockToken.length() > MAX_LOCK_TOKEN_LENGTH) {
            throw new IllegalArgumentException("lockToken은 100자 이하여야 합니다.");
        }

        List<MatchPool> pools = matchPoolRepository.findPoolEntryClaimablePoolsForUpdate(
                requesterPoolId,
                requesterMemberId,
                festivalId,
                now,
                limit
        );
        boolean requesterIncluded = pools.stream().anyMatch(pool -> pool.getId() == requesterPoolId);
        if (!requesterIncluded) {
            return new MatchPoolClaimResult(lockToken, List.of());
        }
        pools.forEach(pool -> pool.lock(now, lockToken));
        return new MatchPoolClaimResult(lockToken, pools.stream().map(MatchPool::getId).toList());
    }
}
