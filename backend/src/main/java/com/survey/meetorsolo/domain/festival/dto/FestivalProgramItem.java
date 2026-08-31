package com.survey.meetorsolo.domain.festival.dto;

/** 축제 상세 프로그램/세부 일정 한 항목. TourAPI detailInfo2는 별도 시간 필드를 안정적으로 제공하지
 * 않아 {@code time}은 대부분 빈 문자열이다. */
public record FestivalProgramItem(String name, String description, String time) {
}
