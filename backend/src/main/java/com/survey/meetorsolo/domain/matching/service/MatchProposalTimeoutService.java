package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.repository.MatchAttemptRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MatchProposalTimeoutService {
    private static final Logger log = LoggerFactory.getLogger(MatchProposalTimeoutService.class);
    private final Clock clock; private final MatchAttemptRepository attempts;
    private final MatchProposalResponseService responses;
    public MatchProposalTimeoutService(Clock clock, MatchAttemptRepository attempts, MatchProposalResponseService responses) {
        this.clock=clock; this.attempts=attempts; this.responses=responses;
    }
    public MatchProposalTimeoutResult runBatch(int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit은 양수여야 합니다.");
        OffsetDateTime now = OffsetDateTime.now(clock);
        var ids = attempts.findTimeoutCandidateIds(now, limit);
        int changed=0, failed=0;
        for (long id : ids) try { if (responses.timeoutAttempt(id, now) != null) changed++; }
        catch (RuntimeException exception) { failed++; log.warn("proposal timeout 처리에 실패했습니다. attemptId={}", id, exception); }
        return new MatchProposalTimeoutResult(ids.size(), changed, failed);
    }
}
