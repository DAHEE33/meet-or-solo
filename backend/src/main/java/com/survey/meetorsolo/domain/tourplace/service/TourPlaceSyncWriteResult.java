package com.survey.meetorsolo.domain.tourplace.service;

record TourPlaceSyncWriteResult(
        int insertedCount,
        int updatedCount,
        int inactiveCount
) {
}
