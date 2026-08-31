package com.survey.meetorsolo.domain.festival.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FestivalScheduleFilterTest {

    @Test
    void ALL은_상한을_사실상_무제한으로_둬서_기간_조건이_아무것도_걸러내지_않는다() {
        LocalDate today = LocalDate.of(2026, 8, 27);

        FestivalScheduleFilter.DateWindow window = FestivalScheduleFilter.ALL.window(today);

        assertThat(window.start()).isEqualTo(today);
        assertThat(window.end()).isEqualTo(FestivalScheduleFilter.MAX_SCHEDULE_DATE);
        // LocalDate.MAX를 쓰면 Asia/Seoul 타임존 변환에서 오버플로가 나 PostgreSQL이 거부한다.
        assertThat(window.end()).isNotEqualTo(LocalDate.MAX);
    }

    @Test
    void ONGOING은_오늘_하루를_구간으로_삼아_오늘_열리는_축제만_남긴다() {
        LocalDate today = LocalDate.of(2026, 8, 27);

        FestivalScheduleFilter.DateWindow window = FestivalScheduleFilter.ONGOING.window(today);

        assertThat(window.start()).isEqualTo(today);
        assertThat(window.end()).isEqualTo(today);
    }

    @Test
    void THIS_WEEKEND는_다가오는_토요일부터_일요일까지다() {
        // 2026-08-27은 목요일 -> 다가오는 토/일은 8/29~8/30
        FestivalScheduleFilter.DateWindow window =
                FestivalScheduleFilter.THIS_WEEKEND.window(LocalDate.of(2026, 8, 27));

        assertThat(window.start()).isEqualTo(LocalDate.of(2026, 8, 29));
        assertThat(window.end()).isEqualTo(LocalDate.of(2026, 8, 30));
    }

    @Test
    void 오늘이_토요일이면_이번_주말은_오늘부터_내일까지다() {
        FestivalScheduleFilter.DateWindow window =
                FestivalScheduleFilter.THIS_WEEKEND.window(LocalDate.of(2026, 8, 29));

        assertThat(window.start()).isEqualTo(LocalDate.of(2026, 8, 29));
        assertThat(window.end()).isEqualTo(LocalDate.of(2026, 8, 30));
    }

    @Test
    void 오늘이_일요일이면_이번_주말은_오늘_하루다() {
        // 다음 주 토요일로 건너뛰면 "이번 주말"에 이미 열려 있는 축제를 놓친다.
        FestivalScheduleFilter.DateWindow window =
                FestivalScheduleFilter.THIS_WEEKEND.window(LocalDate.of(2026, 8, 30));

        assertThat(window.start()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(window.end()).isEqualTo(LocalDate.of(2026, 8, 30));
    }

    @Test
    void THIS_MONTH는_오늘부터_이번_달_말일까지다() {
        FestivalScheduleFilter.DateWindow window =
                FestivalScheduleFilter.THIS_MONTH.window(LocalDate.of(2026, 8, 27));

        assertThat(window.start()).isEqualTo(LocalDate.of(2026, 8, 27));
        assertThat(window.end()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void THIS_MONTH의_말일은_윤년_2월도_정확히_계산한다() {
        FestivalScheduleFilter.DateWindow window =
                FestivalScheduleFilter.THIS_MONTH.window(LocalDate.of(2028, 2, 10));

        assertThat(window.end()).isEqualTo(LocalDate.of(2028, 2, 29));
    }
}
