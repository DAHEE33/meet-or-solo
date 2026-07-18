package com.survey.meetorsolo.domain.festival.service;

record FestivalSyncWriteResult(
        int insertedCount,
        int updatedCount,
        boolean initialLoad
) {
}
