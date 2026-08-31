package com.survey.meetorsolo.domain.tourplace.service;

import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceSyncData;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlace;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import com.survey.meetorsolo.domain.tourplace.repository.TourPlaceRepository;
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
public class TourPlaceSyncWriter {

    private final TourPlaceRepository tourPlaceRepository;

    public TourPlaceSyncWriter(TourPlaceRepository tourPlaceRepository) {
        this.tourPlaceRepository = tourPlaceRepository;
    }

    /**
     * 한 배치 분량의 insert/update만 즉시 커밋한다. 같은 콘텐츠 타입의 나머지 페이지 수집이
     * 이후에 실패하더라도, 이미 이 메서드로 반영된 배치는 그대로 유지된다.
     */
    @Transactional
    public TourPlaceSyncWriteResult upsertBatch(Collection<TourPlaceSyncData> syncData) {
        List<TourPlaceSyncData> dataList = List.copyOf(syncData);
        if (dataList.isEmpty()) {
            return new TourPlaceSyncWriteResult(0, 0, 0);
        }

        List<String> contentIds = dataList.stream()
                .map(TourPlaceSyncData::contentId)
                .distinct()
                .toList();
        Map<String, TourPlace> existingByContentId = new LinkedHashMap<>();
        for (TourPlace place : tourPlaceRepository.findAllByContentIdIn(contentIds)) {
            existingByContentId.put(place.getContentId(), place);
        }

        int insertedCount = 0;
        int updatedCount = 0;
        List<TourPlace> places = new ArrayList<>(dataList.size());
        for (TourPlaceSyncData data : dataList) {
            TourPlace place = existingByContentId.get(data.contentId());
            if (place == null) {
                place = TourPlace.create(data);
                insertedCount++;
            } else {
                place.synchronize(data);
                updatedCount++;
            }
            places.add(place);
        }

        tourPlaceRepository.saveAll(places);
        tourPlaceRepository.flush();

        return new TourPlaceSyncWriteResult(insertedCount, updatedCount, 0);
    }

    /**
     * 해당 콘텐츠 타입의 모든 페이지가 100% 수집된 뒤에만 호출해야 한다. 일부 페이지만 수집된
     * 상태에서 호출하면 아직 못 받은 페이지에 있던 정상 데이터까지 INACTIVE로 잘못 처리된다.
     */
    @Transactional
    public int markMissingInactive(TourPlaceSyncScope syncScope) {
        OffsetDateTime updatedAt = SeoulDateTime.now();
        if (syncScope.observedContentIds().isEmpty()) {
            return tourPlaceRepository.markAllActiveInScopeInactive(
                    syncScope.contentTypeId(),
                    TourPlaceStatus.ACTIVE,
                    TourPlaceStatus.INACTIVE,
                    updatedAt
            );
        }
        return tourPlaceRepository.markActiveMissingInScopeInactive(
                syncScope.observedContentIds(),
                syncScope.contentTypeId(),
                TourPlaceStatus.ACTIVE,
                TourPlaceStatus.INACTIVE,
                updatedAt
        );
    }

    @Transactional(readOnly = true)
    public long countStoredTourPlaces() {
        return tourPlaceRepository.count();
    }

    @Transactional(readOnly = true)
    public long countStoredTourPlacesByContentType(String contentTypeId) {
        return tourPlaceRepository.countByContentTypeId(contentTypeId);
    }
}
