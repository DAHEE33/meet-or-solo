package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.config.MatchingSchedulerProperties;
import com.survey.meetorsolo.domain.matching.entity.MatchAttempt;
import com.survey.meetorsolo.domain.matching.group.MatchGroupCombination;
import com.survey.meetorsolo.domain.matching.group.MatchGroupComposer;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PoolEntryMatchingOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(PoolEntryMatchingOrchestrationService.class);

    private final Clock clock;
    private final MatchingLockTokenGenerator tokenGenerator;
    private final MatchingSchedulerProperties properties;
    private final PoolEntryMatchPoolClaimService claimService;
    private final MatchingBatchReader batchReader;
    private final MatchGroupComposer groupComposer;
    private final MatchProposalCreationService creationService;
    private final MatchPoolReleaseService releaseService;

    public PoolEntryMatchingOrchestrationService(
            Clock clock,
            MatchingLockTokenGenerator tokenGenerator,
            MatchingSchedulerProperties properties,
            PoolEntryMatchPoolClaimService claimService,
            MatchingBatchReader batchReader,
            MatchGroupComposer groupComposer,
            MatchProposalCreationService creationService,
            MatchPoolReleaseService releaseService
    ) {
        this.clock = clock;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
        this.claimService = claimService;
        this.batchReader = batchReader;
        this.groupComposer = groupComposer;
        this.creationService = creationService;
        this.releaseService = releaseService;
    }

    public MatchingOrchestrationResult run(long requesterPoolId, long requesterMemberId, long festivalId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String lockToken = tokenGenerator.generate();
        int claimedCount = 0;
        int failedGroups = 0;
        int releasedCount = 0;
        List<Long> attemptIds = List.of();
        RuntimeException originalFailure = null;
        try {
            MatchPoolClaimResult claim = claimService.claim(
                    requesterPoolId,
                    requesterMemberId,
                    festivalId,
                    now,
                    properties.batchSize(),
                    lockToken
            );
            claimedCount = claim.poolIds().size();
            if (claimedCount == 0) {
                return new MatchingOrchestrationResult(lockToken, 0, List.of(), 0, 0);
            }

            MatchingBatchReader.MatchingBatch batch = batchReader.read(lockToken);
            MatchGroupCombination requesterGroup = groupComposer.compose(
                            batch.candidates(),
                            (left, right) -> !batch.blockedPairs().contains(
                                    MatchingBatchReader.MemberPair.of(left.memberId(), right.memberId())
                            ) && !batch.excludedPairs().contains(MatchOpponentPair.of(
                                    left.memberId(), left.checkinId(), right.memberId(), right.checkinId()))
                    ).stream()
                    .filter(group -> group.candidates().stream()
                            .anyMatch(candidate -> candidate.poolId() == requesterPoolId))
                    .findFirst()
                    .orElse(null);

            if (requesterGroup != null) {
                try {
                    long attemptId = creationService.createInitial(
                            requesterGroup,
                            lockToken,
                            now,
                            properties.proposalTimeout(),
                            MatchAttempt.CREATED_BY_POOL_ENTRY
                    ).attemptId();
                    attemptIds = List.of(attemptId);
                } catch (RuntimeException exception) {
                    failedGroups = 1;
                    log.warn(
                            "pool entry 매칭 proposal 생성에 실패했습니다. poolId={}, token={}",
                            requesterPoolId,
                            lockToken,
                            exception
                    );
                }
            }
        } catch (RuntimeException exception) {
            originalFailure = exception;
            throw exception;
        } finally {
            try {
                releasedCount = releaseService.release(lockToken, now).releasedCount();
                if (releasedCount > 0) {
                    log.info(
                            "pool entry 매칭의 미사용 lock을 release했습니다. poolId={}, token={}, count={}",
                            requesterPoolId,
                            lockToken,
                            releasedCount
                    );
                }
            } catch (RuntimeException releaseFailure) {
                log.error(
                        "pool entry 매칭 lock release에 실패했습니다. poolId={}, token={}",
                        requesterPoolId,
                        lockToken,
                        releaseFailure
                );
                if (originalFailure != null) {
                    originalFailure.addSuppressed(releaseFailure);
                } else {
                    throw releaseFailure;
                }
            }
        }
        return new MatchingOrchestrationResult(
                lockToken,
                claimedCount,
                attemptIds,
                failedGroups,
                releasedCount
        );
    }
}
