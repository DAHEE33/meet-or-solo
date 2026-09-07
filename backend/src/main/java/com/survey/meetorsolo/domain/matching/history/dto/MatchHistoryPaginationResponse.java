package com.survey.meetorsolo.domain.matching.history.dto;

public record MatchHistoryPaginationResponse(int size, boolean hasNext, String nextCursor) {
}
