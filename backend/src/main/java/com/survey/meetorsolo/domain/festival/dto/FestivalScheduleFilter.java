package com.survey.meetorsolo.domain.festival.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * 축제 목록의 일정 필터. 축제는 기간(시작일~종료일)을 가지므로 "특정 날짜에 열리는가"가 아니라
 * **"선택한 기간과 겹치는가"**로 판단한다.
 *
 * <p>겹침 판정은 repository JPQL에서
 * {@code eventStartDate <= windowEnd and eventEndDate >= windowStart}로 수행하며, 날짜가
 * {@code null}인 축제(동기화 데이터 불완전)는 열린 구간으로 취급해 배제하지 않는다 — 기존
 * 목록 동작을 바꾸지 않기 위한 선택이다.
 *
 * <p>화면 표시용 status(ongoing/upcoming/ended)는 프론트의 {@code resolveDisplayStatus}가
 * 계산한다는 기존 결정(docs/13_FESTIVAL_TOURSPOT_API_DESIGN.md 3.2)을 뒤집지 않기 위해,
 * 서버는 status를 필터하지 않고 날짜 구간만 다룬다.
 */
public enum FestivalScheduleFilter {

    /** 필터 없음. 기존 동작과 동일한 기본값이다. */
    ALL,

    /** 오늘 열리고 있는 축제. */
    ONGOING,

    /** 다가오는 주말(토~일). 오늘이 일요일이면 오늘 하루만 본다. */
    THIS_WEEKEND,

    /** 오늘부터 이번 달 말일까지. */
    THIS_MONTH;

    /**
     * {@link #ALL}의 상한으로 쓰는 "사실상 무제한" 날짜.
     *
     * <p>{@link LocalDate#MAX}(+999999999-12-31)를 쓰면 안 된다. 이 프로젝트는
     * {@code hibernate.jdbc.time_zone: Asia/Seoul}로 타임존 변환을 적용하는데, 그 과정에서
     * 최대 연도에 +9시간이 더해지며 오버플로가 나서 PostgreSQL이
     * {@code ERROR: date out of range: "169104628-12-09 BC +09"}로 거부한다.
     * 축제 기간이 9999년을 넘는 경우는 없으므로 이 값으로 충분하다.
     */
    public static final LocalDate MAX_SCHEDULE_DATE = LocalDate.of(9999, 12, 31);

    /** 조회 기준일(KST)로부터 이 필터가 의미하는 날짜 구간을 계산한다. */
    public DateWindow window(LocalDate today) {
        return switch (this) {
            // 상한을 사실상 무제한으로 두어 기간 조건이 아무것도 걸러내지 않게 한다.
            case ALL -> new DateWindow(today, MAX_SCHEDULE_DATE);
            case ONGOING -> new DateWindow(today, today);
            case THIS_WEEKEND -> thisWeekend(today);
            case THIS_MONTH -> new DateWindow(today, today.with(TemporalAdjusters.lastDayOfMonth()));
        };
    }

    private DateWindow thisWeekend(LocalDate today) {
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return new DateWindow(today, today);
        }
        LocalDate saturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        return new DateWindow(saturday, saturday.plusDays(1));
    }

    /** 겹침 판정에 쓰는 날짜 구간. 양 끝을 포함한다. */
    public record DateWindow(LocalDate start, LocalDate end) {
    }
}
