package com.survey.meetorsolo.domain.matching.group;

import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.ACTIVE;
import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.FOOD;
import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.PHOTO;
import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.matching.scoring.TravelStyleScorer;
import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchGroupComposerTest {

    private static final OffsetDateTime BASE_TIME =
            OffsetDateTime.of(2026, 7, 20, 12, 0, 0, 0, ZoneOffset.ofHours(9));
    private final MatchGroupComposer composer = new MatchGroupComposer(new TravelStyleScorer());

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
    void 서로_다른_희망_인원이나_축제의_후보를_같은_그룹으로_섞지_않는다() {
        List<MatchingCandidate> candidates = List.of(
                candidate(1, 2, PHOTO),
                candidate(2, 3, PHOTO),
                candidate(3, 2, 2L, BASE_TIME, true, PHOTO)
        );

        assertThat(composer.compose(candidates)).isEmpty();
    }

    @Test
    void allowMinimumTwo는_최초_그룹_조합에_영향을_주지_않는다() {
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

    @Test
    void 후보가_희망_인원보다_적으면_불완전한_그룹을_만들지_않는다() {
        assertThat(composer.compose(List.of(
                candidate(1, 4, PHOTO),
                candidate(2, 4, PHOTO),
                candidate(3, 4, PHOTO)
        ))).isEmpty();
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
