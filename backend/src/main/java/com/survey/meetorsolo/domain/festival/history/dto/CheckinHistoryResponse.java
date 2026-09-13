package com.survey.meetorsolo.domain.festival.history.dto;

import java.util.List;

public record CheckinHistoryResponse(
        List<CheckinHistoryItemResponse> items,
        CheckinHistoryPaginationResponse pagination
) {
}
