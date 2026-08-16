package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchPoolCleanupService {

    private final MatchPoolRepository matchPoolRepository;

    public MatchPoolCleanupService(MatchPoolRepository matchPoolRepository) {
        this.matchPoolRepository = matchPoolRepository;
    }

    @Transactional
    public MatchPoolCleanupResult cleanup(OffsetDateTime now, OffsetDateTime staleBefore) {
        Objects.requireNonNull(now, "now는 필수입니다.");
        Objects.requireNonNull(staleBefore, "staleBefore는 필수입니다.");
        if (staleBefore.isAfter(now)) {
            throw new IllegalArgumentException("staleBefore는 now 이후일 수 없습니다.");
        }

        int expiredWaitingCount = matchPoolRepository.expireWaitingPools(now);
        int expiredStaleLockedCount = matchPoolRepository.expireStaleLockedPools(now, staleBefore);
        int releasedStaleLockedCount = matchPoolRepository.releaseStaleLockedPools(now, staleBefore);

        return new MatchPoolCleanupResult(
                expiredWaitingCount,
                expiredStaleLockedCount,
                releasedStaleLockedCount
        );
    }
}
