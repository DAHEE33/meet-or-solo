package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import java.time.LocalDate;
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

    public FestivalSyncWriter(FestivalRepository festivalRepository) {
        this.festivalRepository = festivalRepository;
    }

    @Transactional
    public FestivalSyncWriteResult upsert(
            Collection<FestivalSyncData> syncData,
            LocalDate syncDate
    ) {
        boolean initialLoad = festivalRepository.count() == 0;
        if (syncData.isEmpty()) {
            return new FestivalSyncWriteResult(0, 0, initialLoad);
        }

        List<String> contentIds = syncData.stream()
                .map(FestivalSyncData::contentId)
                .distinct()
                .toList();
        Map<String, Festival> existingByContentId = new LinkedHashMap<>();
        for (Festival festival : festivalRepository.findAllByContentIdIn(contentIds)) {
            existingByContentId.put(festival.getContentId(), festival);
        }

        int insertedCount = 0;
        int updatedCount = 0;
        List<Festival> festivals = new ArrayList<>(syncData.size());
        for (FestivalSyncData data : syncData) {
            Festival festival = existingByContentId.get(data.contentId());
            if (festival == null) {
                festival = Festival.create(data, syncDate);
                insertedCount++;
            } else {
                festival.synchronize(data, syncDate);
                updatedCount++;
            }
            festivals.add(festival);
        }

        festivalRepository.saveAll(festivals);
        festivalRepository.flush();
        return new FestivalSyncWriteResult(insertedCount, updatedCount, initialLoad);
    }

    @Transactional(readOnly = true)
    public long countStoredFestivals() {
        return festivalRepository.count();
    }
}
