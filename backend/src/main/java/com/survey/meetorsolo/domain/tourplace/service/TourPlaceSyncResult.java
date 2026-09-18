package com.survey.meetorsolo.domain.tourplace.service;

import java.time.OffsetDateTime;

public record TourPlaceSyncResult(
        int fetchedCount,
        int synchronizedCount,
        int insertedCount,
        int updatedCount,
        int inactiveCount,
        int skippedCount,
        boolean initialLoad,
        OffsetDateTime syncedAt
) {
}
