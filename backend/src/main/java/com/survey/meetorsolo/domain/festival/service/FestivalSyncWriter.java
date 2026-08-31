package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalImage;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalImageRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FestivalSyncWriter {

    private final FestivalRepository festivalRepository;
    private final FestivalImageRepository festivalImageRepository;

    public FestivalSyncWriter(
            FestivalRepository festivalRepository,
            FestivalImageRepository festivalImageRepository
    ) {
        this.festivalRepository = festivalRepository;
        this.festivalImageRepository = festivalImageRepository;
    }

    @Transactional
    public FestivalSyncWriteResult upsert(
            Collection<FestivalSyncData> syncData,
            FestivalSyncScope syncScope
    ) {
        boolean initialLoad = festivalRepository.count() == 0;
        List<FestivalSyncData> dataList = List.copyOf(syncData);

        List<String> contentIds = dataList.stream()
                .map(FestivalSyncData::contentId)
                .distinct()
                .toList();
        Map<String, Festival> existingByContentId = new LinkedHashMap<>();
        if (!contentIds.isEmpty()) {
            for (Festival festival : festivalRepository.findAllByContentIdIn(contentIds)) {
                existingByContentId.put(festival.getContentId(), festival);
            }
        }

        int insertedCount = 0;
        int updatedCount = 0;
        List<Festival> festivals = new ArrayList<>(syncData.size());
        for (FestivalSyncData data : dataList) {
            Festival festival = existingByContentId.get(data.contentId());
            if (festival == null) {
                festival = Festival.create(data, syncScope.syncDate());
                insertedCount++;
            } else {
                festival.synchronize(data, syncScope.syncDate());
                updatedCount++;
            }
            festivals.add(festival);
        }

        int synchronizedImageCount = 0;
        if (!festivals.isEmpty()) {
            festivalRepository.saveAll(festivals);
            festivalRepository.flush();
            synchronizedImageCount = synchronizeRepresentativeImages(dataList, festivals);
        }
        OffsetDateTime statusUpdatedAt = SeoulDateTime.now();
        int endedCount = festivalRepository.markEndedBefore(
                syncScope.syncDate(),
                FestivalStatus.ENDED,
                FestivalStatus.HIDDEN,
                statusUpdatedAt
        );
        int inactiveCount = markMissingFestivalsInactive(syncScope, statusUpdatedAt);
        return new FestivalSyncWriteResult(
                insertedCount,
                updatedCount,
                synchronizedImageCount,
                endedCount,
                inactiveCount,
                initialLoad
        );
    }

    private int markMissingFestivalsInactive(
            FestivalSyncScope syncScope,
            OffsetDateTime updatedAt
    ) {
        if (syncScope.observedContentIds().isEmpty()) {
            return festivalRepository.markAllActiveInScopeInactive(
                    syncScope.eventStartDate(),
                    syncScope.eventEndDate(),
                    syncScope.regionCode(),
                    FestivalStatus.ACTIVE,
                    FestivalStatus.INACTIVE,
                    updatedAt
            );
        }
        return festivalRepository.markActiveMissingInScopeInactive(
                syncScope.observedContentIds(),
                syncScope.eventStartDate(),
                syncScope.eventEndDate(),
                syncScope.regionCode(),
                FestivalStatus.ACTIVE,
                FestivalStatus.INACTIVE,
                updatedAt
        );
    }

    private int synchronizeRepresentativeImages(
            List<FestivalSyncData> syncData,
            List<Festival> festivals
    ) {
        if (syncData.stream().noneMatch(data -> data.originImageUrl() != null)) {
            return 0;
        }
        List<Long> festivalIds = festivals.stream()
                .map(Festival::getId)
                .toList();
        Map<Long, FestivalImage> existingByFestivalId = new LinkedHashMap<>();
        for (FestivalImage image : festivalImageRepository.findAllByFestivalIdIn(festivalIds)) {
            existingByFestivalId.putIfAbsent(image.getFestivalId(), image);
        }

        List<FestivalImage> imagesToSave = new ArrayList<>();
        for (int index = 0; index < syncData.size(); index++) {
            FestivalSyncData data = syncData.get(index);
            if (data.originImageUrl() == null) {
                continue;
            }
            Festival festival = festivals.get(index);
            FestivalImage image = existingByFestivalId.get(festival.getId());
            if (image == null) {
                image = FestivalImage.representative(
                        festival,
                        data.originImageUrl(),
                        data.thumbnailUrl()
                );
            } else {
                image.updateRepresentative(data.originImageUrl(), data.thumbnailUrl());
            }
            imagesToSave.add(image);
        }

        if (!imagesToSave.isEmpty()) {
            festivalImageRepository.saveAll(imagesToSave);
            festivalImageRepository.flush();
        }
        return imagesToSave.size();
    }

    @Transactional(readOnly = true)
    public long countStoredFestivals() {
        return festivalRepository.count();
    }
}
