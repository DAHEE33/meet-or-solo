package com.survey.meetorsolo.domain.festival.dto;

import java.util.List;

/** TourAPI detailCommon2/detailIntro2/detailInfo2를 온디맨드로 합친 축제 부가 정보. */
public record FestivalDetailInfo(
        String intro,
        List<FestivalInfoItem> infoItems,
        List<FestivalProgramItem> programs
) {

    public FestivalDetailInfo {
        infoItems = List.copyOf(infoItems);
        programs = List.copyOf(programs);
    }

    public static FestivalDetailInfo empty() {
        return new FestivalDetailInfo("", List.of(), List.of());
    }

    public boolean isEmpty() {
        return intro.isBlank() && infoItems.isEmpty() && programs.isEmpty();
    }
}
