package com.survey.meetorsolo.domain.matching.history.dto;

import java.util.List;

public record MatchHistoryResponse(
        List<MatchHistoryItemResponse> items,
        MatchHistoryPaginationResponse pagination
) {
}
