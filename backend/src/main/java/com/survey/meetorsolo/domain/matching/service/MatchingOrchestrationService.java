package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.config.MatchingSchedulerProperties;
import com.survey.meetorsolo.domain.matching.group.MatchGroupCombination;
import com.survey.meetorsolo.domain.matching.group.MatchGroupComposer;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MatchingOrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(MatchingOrchestrationService.class);
    private final Clock clock;
    private final MatchingLockTokenGenerator tokenGenerator;
    private final MatchingSchedulerProperties properties;
    private final MatchPoolCleanupService cleanupService;
    private final SchedulerMatchPoolClaimService claimService;
    private final MatchingBatchReader batchReader;
    private final MatchGroupComposer groupComposer;
    private final MatchProposalCreationService creationService;
    private final MatchPoolReleaseService releaseService;

    public MatchingOrchestrationService(Clock clock, MatchingLockTokenGenerator tokenGenerator,
            MatchingSchedulerProperties properties, MatchPoolCleanupService cleanupService,
            SchedulerMatchPoolClaimService claimService, MatchingBatchReader batchReader,
            MatchGroupComposer groupComposer, MatchProposalCreationService creationService,
            MatchPoolReleaseService releaseService) {
        this.clock = clock; this.tokenGenerator = tokenGenerator; this.properties = properties;
        this.cleanupService = cleanupService; this.claimService = claimService; this.batchReader = batchReader;
        this.groupComposer = groupComposer; this.creationService = creationService; this.releaseService = releaseService;
    }

    public MatchingOrchestrationResult runTick() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String lockToken = tokenGenerator.generate();
        int claimedCount = 0;
        int failedGroups = 0;
        int releasedCount = 0;
        List<Long> attemptIds = new ArrayList<>();
        RuntimeException originalFailure = null;
        try {
            cleanupService.cleanup(now, now.minus(properties.staleTimeout()));
            MatchPoolClaimResult claim = claimService.claim(now, properties.batchSize(), lockToken);
            claimedCount = claim.poolIds().size();
            if (claimedCount == 0) return new MatchingOrchestrationResult(lockToken, 0, List.of(), 0, 0);
            MatchingBatchReader.MatchingBatch batch = batchReader.read(lockToken);
            List<MatchGroupCombination> groups = groupComposer.compose(batch.candidates(), (left, right) ->
                    !batch.blockedPairs().contains(MatchingBatchReader.MemberPair.of(left.memberId(), right.memberId())));
            for (MatchGroupCombination group : groups) {
                try {
                    attemptIds.add(creationService.createInitial(
                            group, lockToken, now, properties.proposalTimeout()).attemptId());
                } catch (RuntimeException exception) {
                    failedGroups++;
                    log.warn("매칭 그룹 proposal 생성에 실패했습니다. token={}, poolIds={}", lockToken,
                            group.candidates().stream().map(candidate -> candidate.poolId()).toList(), exception);
                }
            }
        } catch (RuntimeException exception) {
            originalFailure = exception;
            throw exception;
        } finally {
            try {
                releasedCount = releaseService.release(lockToken, now).releasedCount();
                if (releasedCount > 0) log.info("미사용 매칭 lock을 release했습니다. token={}, count={}", lockToken, releasedCount);
            } catch (RuntimeException releaseFailure) {
                log.error("매칭 lock release에 실패했습니다. token={}", lockToken, releaseFailure);
                if (originalFailure != null) originalFailure.addSuppressed(releaseFailure);
                else throw releaseFailure;
            }
        }
        return new MatchingOrchestrationResult(lockToken, claimedCount, attemptIds, failedGroups, releasedCount);
    }
}
