package com.survey.meetorsolo.domain.matching.group;

import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.ACTIVE;
import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.FOOD;
import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.PHOTO;
import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.matching.scoring.EmbeddingScorer;
import com.survey.meetorsolo.domain.matching.scoring.PairCompatibilityScorer;
import com.survey.meetorsolo.domain.matching.scoring.TravelStyleScorer;
import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchGroupComposerTest {

    private static final OffsetDateTime BASE_TIME =
            OffsetDateTime.of(2026, 7, 20, 12, 0, 0, 0, ZoneOffset.ofHours(9));
    private final MatchGroupComposer composer = new MatchGroupComposer(new PairCompatibilityScorer(
            new TravelStyleScorer(), new EmbeddingScorer(), new BigDecimal("0.70"), new BigDecimal("0.30")));

    @Test
    void 희망_인원에_맞는_2인_3인_4인_그룹을_구성한다() {
        List<MatchingCandidate> candidates = List.of(
                candidate(1, 2, PHOTO), candidate(2, 2, PHOTO),
                candidate(3, 3, FOOD), candidate(4, 3, FOOD), candidate(5, 3, FOOD),
                candidate(6, 4, ACTIVE), candidate(7, 4, ACTIVE),
                candidate(8, 4, ACTIVE), candidate(9, 4, ACTIVE)
        );

        List<MatchGroupCombination> groups = composer.compose(candidates);

        assertThat(groups).extracting(group -> group.candidates().size())
                .containsExactlyInAnyOrder(2, 3, 4);
    }

    @Test
    void 그룹_점수는_내부의_모든_pair_점수_평균이다() {
        List<MatchingCandidate> candidates = List.of(
                candidate(1, 3, PHOTO, FOOD),
                candidate(2, 3, PHOTO, ACTIVE),
                candidate(3, 3, PHOTO)
        );

        MatchGroupCombination group = composer.compose(candidates).get(0);

        assertThat(group.score()).isEqualByComparingTo("44.44");
    }

    @Test
    void 결정적_greedy로_가장_높은_점수_조합부터_배정하고_회원을_중복_배정하지_않는다() {
        List<MatchingCandidate> candidates = List.of(
                candidate(1, 2, PHOTO),
                candidate(2, 2, PHOTO),
                candidate(3, 2, FOOD),
                candidate(4, 2, FOOD)
        );

        List<MatchGroupCombination> groups = composer.compose(candidates);

        assertThat(poolIds(groups)).containsExactly(List.of(1L, 2L), List.of(3L, 4L));
        assertThat(groups.stream()
                .flatMap(group -> group.candidates().stream())
                .map(MatchingCandidate::memberId))
                .doesNotHaveDuplicates();
    }

    @Test
    void 동점이면_오래_기다린_후보를_포함한_조합을_우선한다() {
        MatchingCandidate oldest = candidate(3, 3, BASE_TIME.minusSeconds(3), true, PHOTO);
        MatchingCandidate middle = candidate(2, 3, BASE_TIME.minusSeconds(2), true, PHOTO);
        MatchingCandidate newest = candidate(1, 3, BASE_TIME.minusSeconds(1), true, PHOTO);
        MatchingCandidate extra = candidate(4, 3, BASE_TIME, true, PHOTO);

        MatchGroupCombination group = composer.compose(List.of(extra, newest, middle, oldest)).get(0);

        assertThat(group.candidates()).extracting(MatchingCandidate::poolId)
                .containsExactly(3L, 2L, 1L);
    }

    @Test
    void 점수와_대기_시각이_같으면_작은_poolId_조합을_우선한다() {
        List<MatchingCandidate> candidates = List.of(
                candidate(4, 3, BASE_TIME, true, PHOTO),
                candidate(3, 3, BASE_TIME, true, PHOTO),
                candidate(2, 3, BASE_TIME, true, PHOTO),
                candidate(1, 3, BASE_TIME, true, PHOTO)
        );

        MatchGroupCombination group = composer.compose(candidates).get(0);

        assertThat(group.candidates()).extracting(MatchingCandidate::poolId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void 입력_순서가_달라도_동일한_그룹을_생성한다() {
        List<MatchingCandidate> candidates = new ArrayList<>(List.of(
                candidate(1, 2, PHOTO), candidate(2, 2, PHOTO),
                candidate(3, 2, FOOD), candidate(4, 2, FOOD)
        ));
        List<List<Long>> expected = poolIds(composer.compose(candidates));

        Collections.reverse(candidates);

        assertThat(poolIds(composer.compose(candidates))).isEqualTo(expected);
    }

    @Test
    void 다른_축제의_후보는_같은_그룹으로_섞지_않는다() {
        List<MatchingCandidate> candidates = List.of(
                candidate(1, 2, PHOTO),
                candidate(3, 2, 2L, BASE_TIME, true, PHOTO)
        );

        assertThat(composer.compose(candidates)).isEmpty();
    }

    /**
     * 희망 인원이 달라도 <b>작은 쪽에 맞춰</b> 묶는다.
     *
     * <p>예전에는 희망 인원으로도 버킷을 나눠서, 2명을 고른 사람과 3명을 고른 사람은 같은 축제에
     * 있어도 영원히 만나지 못했다. 사용자에게는 "매칭이 안 된다"로만 보인다.
     */
    @Test
    void 희망_인원이_달라도_작은_쪽에_맞춰_묶는다() {
        List<MatchGroupCombination> groups = composer.compose(List.of(
                candidate(1, 2, PHOTO),
                candidate(2, 3, PHOTO)
        ));

        assertThat(groups).hasSize(1);
        assertThat(poolIds(groups).get(0)).containsExactly(1L, 2L);
    }

    /** 희망보다 큰 그룹은 누구에게도 강요하지 않는다. */
    @Test
    void 희망_인원보다_큰_그룹은_만들지_않는다() {
        List<MatchGroupCombination> groups = composer.compose(List.of(
                candidate(1, 2, PHOTO),
                candidate(2, 2, PHOTO),
                candidate(3, 2, PHOTO)
        ));

        assertThat(groups).hasSize(1);
        assertThat(poolIds(groups).get(0)).hasSize(2);
    }

    /** 희망 인원이 그대로 채워지면 동의 여부와 무관하게 같은 결과다. */
    @Test
    void 희망_인원이_정확히_채워지면_allowMinimumTwo는_결과를_바꾸지_않는다() {
        List<MatchingCandidate> allowed = List.of(
                candidate(1, 3, BASE_TIME, true, PHOTO),
                candidate(2, 3, BASE_TIME, true, PHOTO),
                candidate(3, 3, BASE_TIME, true, PHOTO)
        );
        List<MatchingCandidate> notAllowed = List.of(
                candidate(1, 3, BASE_TIME, false, PHOTO),
                candidate(2, 3, BASE_TIME, false, PHOTO),
                candidate(3, 3, BASE_TIME, false, PHOTO)
        );

        assertThat(poolIds(composer.compose(allowed)))
                .isEqualTo(poolIds(composer.compose(notAllowed)));
    }

    /**
     * 사용자 제보로 드러난 문제다. 3명을 희망한 두 사람이 "2명이어도 괜찮아요"에 동의했는데도
     * 매칭이 되지 않았다. 조합 단계에서 그 동의를 읽지 않았기 때문이다.
     */
    @Test
    void 희망보다_적어도_전원이_동의하면_줄여서_묶는다() {
        List<MatchGroupCombination> groups = composer.compose(List.of(
                candidate(1, 3, BASE_TIME, true, PHOTO),
                candidate(2, 3, BASE_TIME, true, PHOTO)
        ));

        assertThat(groups).hasSize(1);
        assertThat(poolIds(groups).get(0)).containsExactly(1L, 2L);
    }

    /** 한 명이라도 동의하지 않으면 줄이지 않는다. 사전에 받아둔 의사를 무시하면 안 된다. */
    @Test
    void 한_명이라도_동의하지_않으면_줄여서_묶지_않는다() {
        assertThat(composer.compose(List.of(
                candidate(1, 3, BASE_TIME, true, PHOTO),
                candidate(2, 3, BASE_TIME, false, PHOTO)
        ))).isEmpty();
    }

    /** 3명이 모일 수 있으면 2명으로 성급히 쪼개지 않는다. */
    @Test
    void 큰_조합이_가능하면_먼저_묶는다() {
        List<MatchGroupCombination> groups = composer.compose(List.of(
                candidate(1, 3, BASE_TIME, true, PHOTO),
                candidate(2, 3, BASE_TIME, true, PHOTO),
                candidate(3, 3, BASE_TIME, true, PHOTO)
        ));

        assertThat(groups).hasSize(1);
        assertThat(poolIds(groups).get(0)).hasSize(3);
    }

    /**
     * 태그를 상수로 고정하면 코사인만 순위를 가른다.
     *
     * <p>희망 인원을 전원 2명으로 두는 것이 이 검증의 전제다. 3인을 허용하면 조합 우선순위가
     * 점수보다 인원 수를 먼저 보므로 세 명이 한 그룹으로 묶여 점수 검증이 되지 않는다.
     * 바로 아래 테스트가 그 규칙을 따로 확인한다.
     *
     * <p>이 테스트가 이번 변경(즉시 조합 경로 제거)의 회귀 방지선이다. 신청 직후 즉시 조합하면
     * 후보가 2명을 넘지 못해 조합이 1개뿐이고, 그러면 아래 기대값이 코사인과 무관하게
     * 만족돼 버린다. 후보 3명이 한 배치에 들어오는 경로가 유지돼야 이 검증이 의미를 가진다.
     */
    @Test
    void 태그가_같고_희망_인원이_2명이면_코사인이_가장_높은_pair를_고른다() {
        // 태그는 전원 PHOTO 하나뿐이므로 모든 pair의 Jaccard가 100으로 같다.
        // 1-2 코사인 약 0.99, 1-3은 0, 2-3은 약 0.14다.
        List<MatchGroupCombination> groups = composer.compose(List.of(
                candidate(1, 2, new float[] {1.0f, 0.0f}, PHOTO),
                candidate(2, 2, new float[] {0.99f, 0.14f}, PHOTO),
                candidate(3, 2, new float[] {0.0f, 1.0f}, PHOTO)
        ));

        assertThat(groups).hasSize(1);
        assertThat(poolIds(groups).get(0)).containsExactly(1L, 2L);
    }

    /** 궁합 점수는 같은 인원 조합 사이의 순위만 가린다. 인원 수가 먼저다. */
    @Test
    void 전원이_3인을_허용하면_궁합_점수보다_인원_수가_먼저다() {
        List<MatchGroupCombination> groups = composer.compose(List.of(
                candidate(1, 3, new float[] {1.0f, 0.0f}, PHOTO),
                candidate(2, 3, new float[] {0.99f, 0.14f}, PHOTO),
                candidate(3, 3, new float[] {0.0f, 1.0f}, PHOTO)
        ));

        // 1-2만 묶으면 pair 점수가 더 높지만, 3인 조합이 가능하므로 그쪽이 먼저다.
        assertThat(groups).hasSize(1);
        assertThat(poolIds(groups).get(0)).containsExactly(1L, 2L, 3L);
    }

    /** 임베딩 보유자와 미보유자가 섞여도 조합은 만들어진다(미보유 pair는 태그 점수만 쓴다). */
    @Test
    void 임베딩_미보유_후보가_섞여도_조합에서_제외하지_않는다() {
        List<MatchGroupCombination> groups = composer.compose(List.of(
                candidate(1, 2, new float[] {1.0f, 0.0f}, PHOTO),
                candidate(2, 2, (float[]) null, PHOTO)
        ));

        assertThat(groups).hasSize(1);
        assertThat(poolIds(groups).get(0)).containsExactly(1L, 2L);
    }

    private MatchingCandidate candidate(
            long id,
            int groupSize,
            float[] preferenceEmbedding,
            TravelStyleCode... styles
    ) {
        return new MatchingCandidate(
                id,
                100L + id,
                id,
                1L,
                groupSize,
                true,
                BASE_TIME.plusSeconds(id),
                List.of(styles),
                preferenceEmbedding
        );
    }

    private MatchingCandidate candidate(long id, int groupSize, TravelStyleCode... styles) {
        return candidate(id, groupSize, 1L, BASE_TIME.plusSeconds(id), true, styles);
    }

    private MatchingCandidate candidate(
            long id,
            int groupSize,
            OffsetDateTime enteredAt,
            boolean allowMinimumTwo,
            TravelStyleCode... styles
    ) {
        return candidate(id, groupSize, 1L, enteredAt, allowMinimumTwo, styles);
    }

    private MatchingCandidate candidate(
            long id,
            int groupSize,
            long festivalId,
            OffsetDateTime enteredAt,
            boolean allowMinimumTwo,
            TravelStyleCode... styles
    ) {
        return new MatchingCandidate(
                id,
                100L + id,
                id,
                festivalId,
                groupSize,
                allowMinimumTwo,
                enteredAt,
                List.of(styles)
        );
    }

    private List<List<Long>> poolIds(List<MatchGroupCombination> groups) {
        return groups.stream()
                .map(group -> group.candidates().stream().map(MatchingCandidate::poolId).toList())
                .toList();
    }
}
