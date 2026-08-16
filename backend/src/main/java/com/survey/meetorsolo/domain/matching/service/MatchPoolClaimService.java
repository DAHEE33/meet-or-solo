package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchPoolClaimService {

    private static final int MAX_LOCK_TOKEN_LENGTH = 100;

    private final MatchPoolRepository matchPoolRepository;

    public MatchPoolClaimService(MatchPoolRepository matchPoolRepository) {
        this.matchPoolRepository = matchPoolRepository;
    }

    @Transactional
    public MatchPoolClaimResult claimCandidates(
            Long festivalId,
            Long requesterMemberId,
            OffsetDateTime now,
            int limit,
            String lockToken
    ) {
        validateInputs(festivalId, requesterMemberId, now, limit, lockToken);

        List<MatchPool> claimedPools = matchPoolRepository.findEligibleWaitingCandidatesForUpdate(
                festivalId,
                requesterMemberId,
                now,
                limit
        );
        claimedPools.forEach(pool -> pool.lock(now, lockToken));

        return new MatchPoolClaimResult(
                lockToken,
                claimedPools.stream().map(MatchPool::getId).toList()
        );
    }

    private void validateInputs(
            Long festivalId,
            Long requesterMemberId,
            OffsetDateTime now,
            int limit,
            String lockToken
    ) {
        Objects.requireNonNull(festivalId, "festivalId는 필수입니다.");
        Objects.requireNonNull(requesterMemberId, "requesterMemberId는 필수입니다.");
        Objects.requireNonNull(now, "now는 필수입니다.");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
        }
        if (lockToken == null || lockToken.isBlank()) {
            throw new IllegalArgumentException("lockToken은 필수입니다.");
        }
        if (lockToken.length() > MAX_LOCK_TOKEN_LENGTH) {
            throw new IllegalArgumentException("lockToken은 100자 이하여야 합니다.");
        }
    }
}
