package com.survey.meetorsolo.domain.festival.service;

record FestivalSyncWriteResult(
        int insertedCount,
        int updatedCount,
        int synchronizedImageCount,
        int endedCount,
        int inactiveCount,
        boolean initialLoad
) {
}
