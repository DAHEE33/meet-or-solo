package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class MatchOpponentPairTest {

    @Test
    void member와_checkin의_대응을_유지한_채_pair를_정규화한다() {
        MatchOpponentPair pair = MatchOpponentPair.of(20L, 200L, 10L, 100L);

        assertThat(pair).isEqualTo(new MatchOpponentPair(10L, 20L, 100L, 200L));
    }

    @Test
    void 동일_pair는_방향과_무관하게_같은_advisory_key를_사용한다() {
        MatchOpponentPair forward = MatchOpponentPair.of(10L, 100L, 20L, 200L);
        MatchOpponentPair reverse = MatchOpponentPair.of(20L, 200L, 10L, 100L);

        assertThat(forward.advisoryLockKey()).isEqualTo(reverse.advisoryLockKey());
    }

    @Test
    void 여러_pair는_모든_경로에서_사용할_결정적_순서로_정렬된다() {
        MatchOpponentPair first = MatchOpponentPair.of(10L, 101L, 20L, 201L);
        MatchOpponentPair second = MatchOpponentPair.of(10L, 101L, 30L, 301L);
        MatchOpponentPair third = MatchOpponentPair.of(20L, 201L, 30L, 301L);

        assertThat(List.of(third, second, first).stream().sorted().toList())
                .containsExactly(first, second, third);
    }

    @Test
    void 동일_회원_pair는_거부한다() {
        assertThatThrownBy(() -> MatchOpponentPair.of(10L, 100L, 10L, 101L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
