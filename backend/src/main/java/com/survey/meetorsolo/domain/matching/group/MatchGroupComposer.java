package com.survey.meetorsolo.domain.matching.group;

import com.survey.meetorsolo.domain.matching.scoring.PairCompatibilityScorer;
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
import java.util.function.BiPredicate;

/**
 * 대기 중인 후보를 그룹 조합으로 묶는다.
 *
 * <p><b>희망 인원은 상한이지 고정값이 아니다</b>({@code docs/05}). 예전에는 후보를
 * {@code (축제, 희망 인원)}으로 버킷을 나누고 그 인원짜리 조합만 만들었다. 그래서 3명을 희망한
 * 사람 둘은 <b>영원히 매칭되지 않았고</b>, 희망 인원이 다른 사람끼리도 절대 만나지 못했다.
 * "2명이어도 괜찮아요"({@code allowMinimumTwo})는 조합 단계에서 읽히지도 않았다.
 *
 * <p>이제 같은 축제 후보를 한데 모아 <b>큰 조합부터</b> 시도한다. 어떤 조합이 유효하려면 두
 * 조건을 모두 만족해야 한다.
 *
 * <ol>
 *   <li>조합 크기가 구성원 <b>각자의 희망 인원 이하</b>여야 한다. 원하지 않은 큰 그룹에 넣지
 *       않는다.</li>
 *   <li>자기 희망보다 작은 조합에 들어가는 사람은 {@code allowMinimumTwo}에 동의했어야 한다.</li>
 * </ol>
 *
 * <p>큰 조합을 먼저 고르므로 3명이 모여 있으면 2명으로 성급히 쪼개지 않는다. 다만 같은 tick에
 * 두 명뿐이면 바로 2명으로 묶인다. 검색 창이 60초뿐이라 "조금 더 기다려보기"의 실익보다
 * 못 만나고 끝나는 손해가 크다고 봤다.
 */
public class MatchGroupComposer {

    /** 그룹 인원의 하한. {@code docs/05}의 최소 매칭 인원과 같다. */
    private static final int MINIMUM_GROUP_SIZE = 2;
    /** 그룹 인원의 상한. 희망 인원 선택지의 최대값이다. */
    private static final int MAXIMUM_GROUP_SIZE = 4;

    private static final Comparator<MatchingCandidate> CANDIDATE_ORDER =
            Comparator.comparing(MatchingCandidate::enteredAt)
                    .thenComparingLong(MatchingCandidate::poolId);

    private final PairCompatibilityScorer pairScorer;

    public MatchGroupComposer(PairCompatibilityScorer pairScorer) {
        this.pairScorer = Objects.requireNonNull(pairScorer, "pairScorer는 필수입니다.");
    }

    public List<MatchGroupCombination> compose(Collection<MatchingCandidate> inputCandidates) {
        return compose(inputCandidates, (left, right) -> true);
    }

    public List<MatchGroupCombination> compose(
            Collection<MatchingCandidate> inputCandidates,
            BiPredicate<MatchingCandidate, MatchingCandidate> compatibility
    ) {
        Objects.requireNonNull(inputCandidates, "inputCandidates는 필수입니다.");
        Objects.requireNonNull(compatibility, "compatibility는 필수입니다.");
        List<MatchingCandidate> candidates = inputCandidates.stream()
                .map(candidate -> Objects.requireNonNull(candidate, "inputCandidates에는 null을 포함할 수 없습니다."))
                .sorted(CANDIDATE_ORDER)
                .toList();
        validateUniqueCandidates(candidates);

        // 축제로만 나눈다. 희망 인원으로 더 쪼개면 3명 희망끼리는 2명이 모여도 만나지 못한다.
        Map<Long, List<MatchingCandidate>> festivalBuckets = new LinkedHashMap<>();
        for (MatchingCandidate candidate : candidates) {
            festivalBuckets.computeIfAbsent(candidate.festivalId(), ignored -> new ArrayList<>())
                    .add(candidate);
        }

        List<MatchGroupCombination> combinations = new ArrayList<>();
        festivalBuckets.forEach((festivalId, bucket) -> {
            for (int size = MAXIMUM_GROUP_SIZE; size >= MINIMUM_GROUP_SIZE; size--) {
                generateCombinations(bucket, size, 0, new ArrayList<>(), combinations, compatibility);
            }
        });
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
            List<MatchGroupCombination> combinations,
            BiPredicate<MatchingCandidate, MatchingCandidate> compatibility
    ) {
        if (current.size() == groupSize) {
            if (acceptsSize(current, groupSize)) {
                combinations.add(new MatchGroupCombination(current, groupScore(current)));
            }
            return;
        }
        int remainingNeeded = groupSize - current.size();
        for (int index = startIndex; index <= candidates.size() - remainingNeeded; index++) {
            MatchingCandidate candidate = candidates.get(index);
            if (current.stream().anyMatch(selected -> !compatibility.test(selected, candidate))) {
                continue;
            }
            current.add(candidate);
            generateCombinations(candidates, groupSize, index + 1, current, combinations, compatibility);
            current.remove(current.size() - 1);
        }
    }

    private BigDecimal groupScore(List<MatchingCandidate> candidates) {
        BigDecimal total = BigDecimal.ZERO;
        int pairCount = 0;
        for (int left = 0; left < candidates.size() - 1; left++) {
            for (int right = left + 1; right < candidates.size(); right++) {
                MatchingCandidate l = candidates.get(left);
                MatchingCandidate r = candidates.get(right);
                total = total.add(pairScorer.score(
                        l.travelStyles(), r.travelStyles(),
                        l.preferenceEmbedding(), r.preferenceEmbedding()));
                pairCount++;
            }
        }
        return total.divide(
                BigDecimal.valueOf(pairCount),
                PairCompatibilityScorer.SCORE_SCALE,
                RoundingMode.HALF_UP
        );
    }

    /**
     * 구성원 전원이 이 인원을 받아들이는지.
     *
     * <p>희망보다 큰 그룹은 누구에게도 강요하지 않는다. 희망보다 작은 그룹은
     * {@code allowMinimumTwo}에 동의한 사람만 들어간다 — 그 토글의 뜻이 정확히 이것이다.
     */
    private boolean acceptsSize(List<MatchingCandidate> candidates, int groupSize) {
        return candidates.stream().allMatch(candidate -> {
            if (groupSize > candidate.preferredGroupSize()) return false;
            return groupSize == candidate.preferredGroupSize() || candidate.allowMinimumTwo();
        });
    }

    /**
     * 조합 우선순위. <b>인원이 많은 조합이 먼저다.</b>
     *
     * <p>희망 인원을 최대한 맞추는 것이 점수보다 앞선다. 3명이 모일 수 있는데 궁합 점수가 조금
     * 높다고 2명으로 쪼개면, 사용자가 3명을 고른 의미가 사라진다.
     */
    private int compareCombinations(MatchGroupCombination left, MatchGroupCombination right) {
        int sizeComparison = Integer.compare(right.candidates().size(), left.candidates().size());
        if (sizeComparison != 0) {
            return sizeComparison;
        }
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
}
