package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchPoolReleaseService {
    private final MatchPoolRepository matchPoolRepository;
    public MatchPoolReleaseService(MatchPoolRepository matchPoolRepository) {
        this.matchPoolRepository = matchPoolRepository;
    }
    @Transactional
    public MatchPoolReleaseResult release(String lockToken, OffsetDateTime now) {
        if (lockToken == null || lockToken.isBlank()) throw new IllegalArgumentException("lockToken은 필수입니다.");
        Objects.requireNonNull(now, "now는 필수입니다.");
        return new MatchPoolReleaseResult(matchPoolRepository.releaseOwnedLockedPools(lockToken, now));
    }
}
