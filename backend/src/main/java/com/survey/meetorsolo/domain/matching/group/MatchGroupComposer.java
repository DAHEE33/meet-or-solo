package com.survey.meetorsolo.domain.matching.group;

import com.survey.meetorsolo.domain.matching.scoring.TravelStyleScorer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MatchGroupComposer {

    private static final Comparator<MatchingCandidate> CANDIDATE_ORDER =
            Comparator.comparing(MatchingCandidate::enteredAt)
                    .thenComparingLong(MatchingCandidate::poolId);

    private final TravelStyleScorer travelStyleScorer;

    public MatchGroupComposer(TravelStyleScorer travelStyleScorer) {
        this.travelStyleScorer = Objects.requireNonNull(travelStyleScorer, "travelStyleScorer는 필수입니다.");
    }

    public List<MatchGroupCombination> compose(Collection<MatchingCandidate> inputCandidates) {
        Objects.requireNonNull(inputCandidates, "inputCandidates는 필수입니다.");
        List<MatchingCandidate> candidates = inputCandidates.stream()
                .map(candidate -> Objects.requireNonNull(candidate, "inputCandidates에는 null을 포함할 수 없습니다."))
                .sorted(CANDIDATE_ORDER)
                .toList();
        validateUniqueCandidates(candidates);

        Map<GroupKey, List<MatchingCandidate>> compatibleBuckets = new LinkedHashMap<>();
        for (MatchingCandidate candidate : candidates) {
            GroupKey key = new GroupKey(candidate.festivalId(), candidate.preferredGroupSize());
            compatibleBuckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
        }

        List<MatchGroupCombination> combinations = new ArrayList<>();
        compatibleBuckets.forEach((key, bucket) ->
                generateCombinations(bucket, key.groupSize(), 0, new ArrayList<>(), combinations)
        );
        combinations.sort(this::compareCombinations);

        Set<Long> assignedMemberIds = new HashSet<>();
        Set<Long> assignedPoolIds = new HashSet<>();
        List<MatchGroupCombination> selected = new ArrayList<>();
        for (MatchGroupCombination combination : combinations) {
            boolean overlaps = combination.candidates().stream().anyMatch(candidate ->
                    assignedMemberIds.contains(candidate.memberId()) || assignedPoolIds.contains(candidate.poolId())
            );
            if (overlaps) {
                continue;
            }
            selected.add(combination);
            combination.candidates().forEach(candidate -> {
                assignedMemberIds.add(candidate.memberId());
                assignedPoolIds.add(candidate.poolId());
            });
        }
        return List.copyOf(selected);
    }

    private void generateCombinations(
            List<MatchingCandidate> candidates,
            int groupSize,
            int startIndex,
            List<MatchingCandidate> current,
            List<MatchGroupCombination> combinations
    ) {
        if (current.size() == groupSize) {
            combinations.add(new MatchGroupCombination(current, groupScore(current)));
            return;
        }
        int remainingNeeded = groupSize - current.size();
        for (int index = startIndex; index <= candidates.size() - remainingNeeded; index++) {
            current.add(candidates.get(index));
            generateCombinations(candidates, groupSize, index + 1, current, combinations);
            current.remove(current.size() - 1);
        }
    }

    private BigDecimal groupScore(List<MatchingCandidate> candidates) {
        BigDecimal total = BigDecimal.ZERO;
        int pairCount = 0;
        for (int left = 0; left < candidates.size() - 1; left++) {
            for (int right = left + 1; right < candidates.size(); right++) {
                total = total.add(travelStyleScorer.score(
                        candidates.get(left).travelStyles(),
                        candidates.get(right).travelStyles()
                ));
                pairCount++;
            }
        }
        return total.divide(
                BigDecimal.valueOf(pairCount),
                TravelStyleScorer.SCORE_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private int compareCombinations(MatchGroupCombination left, MatchGroupCombination right) {
        int scoreComparison = right.score().compareTo(left.score());
        if (scoreComparison != 0) {
            return scoreComparison;
        }
        int sharedSize = Math.min(left.candidates().size(), right.candidates().size());
        for (int index = 0; index < sharedSize; index++) {
            OffsetDateTime leftEnteredAt = left.candidates().get(index).enteredAt();
            OffsetDateTime rightEnteredAt = right.candidates().get(index).enteredAt();
            int timeComparison = leftEnteredAt.compareTo(rightEnteredAt);
            if (timeComparison != 0) {
                return timeComparison;
            }
        }
        for (int index = 0; index < sharedSize; index++) {
            int poolComparison = Long.compare(
                    left.candidates().get(index).poolId(),
                    right.candidates().get(index).poolId()
            );
            if (poolComparison != 0) {
                return poolComparison;
            }
        }
        return Integer.compare(left.candidates().size(), right.candidates().size());
    }

    private void validateUniqueCandidates(List<MatchingCandidate> candidates) {
        Set<Long> poolIds = new HashSet<>();
        Set<Long> memberIds = new HashSet<>();
        for (MatchingCandidate candidate : candidates) {
            if (!poolIds.add(candidate.poolId())) {
                throw new IllegalArgumentException("중복 poolId는 허용하지 않습니다: " + candidate.poolId());
            }
            if (!memberIds.add(candidate.memberId())) {
                throw new IllegalArgumentException("중복 memberId는 허용하지 않습니다: " + candidate.memberId());
            }
        }
    }

    private record GroupKey(long festivalId, int groupSize) {
    }
}
