package com.survey.meetorsolo.domain.festival.service;

import java.time.OffsetDateTime;

public record FestivalSyncResult(
        int fetchedCount,
        int synchronizedCount,
        int insertedCount,
        int updatedCount,
        int synchronizedImageCount,
        int endedCount,
        int inactiveCount,
        int skippedCount,
        boolean initialLoad,
        OffsetDateTime syncedAt
) {
}
