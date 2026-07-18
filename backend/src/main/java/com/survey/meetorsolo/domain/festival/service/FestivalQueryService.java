package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.festival.dto.FestivalListItemResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalListResponse;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalImage;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalImageRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FestivalQueryService {

    private final FestivalRepository festivalRepository;
    private final FestivalImageRepository festivalImageRepository;

    public FestivalQueryService(
            FestivalRepository festivalRepository,
            FestivalImageRepository festivalImageRepository
    ) {
        this.festivalRepository = festivalRepository;
        this.festivalImageRepository = festivalImageRepository;
    }

    @Transactional(readOnly = true)
    public FestivalListResponse getActiveFestivals(int page, int size) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("eventStartDate"), Sort.Order.asc("id"))
        );
        LocalDate today = LocalDate.now(SeoulDateTime.ZONE_ID);
        Page<Festival> festivalPage = festivalRepository.findVisibleFestivals(
                FestivalStatus.ACTIVE,
                today,
                pageRequest
        );

        Map<Long, FestivalImage> representativeImages = representativeImages(
                festivalPage.getContent()
        );
        List<FestivalListItemResponse> items = festivalPage.getContent().stream()
                .map(festival -> toResponse(
                        festival,
                        representativeImages.get(festival.getId())
                ))
                .toList();
        return new FestivalListResponse(
                items,
                festivalPage.getNumber(),
                festivalPage.getSize(),
                festivalPage.getTotalElements(),
                festivalPage.getTotalPages(),
                festivalPage.hasNext()
        );
    }

    private Map<Long, FestivalImage> representativeImages(List<Festival> festivals) {
        if (festivals.isEmpty()) {
            return Map.of();
        }
        List<Long> festivalIds = festivals.stream()
                .map(Festival::getId)
                .toList();
        Map<Long, FestivalImage> imagesByFestivalId = new LinkedHashMap<>();
        for (FestivalImage image : festivalImageRepository.findAllByFestivalIdIn(festivalIds)) {
            imagesByFestivalId.putIfAbsent(image.getFestivalId(), image);
        }
        return imagesByFestivalId;
    }

    private FestivalListItemResponse toResponse(Festival festival, FestivalImage image) {
        return new FestivalListItemResponse(
                festival.getId(),
                festival.getContentId(),
                festival.getTitle(),
                festival.getAddress(),
                festival.getAreaCode(),
                festival.getSigunguCode(),
                festival.getEventStartDate(),
                festival.getEventEndDate(),
                festival.getStatus(),
                image == null ? null : image.getOriginImageUrl(),
                image == null ? null : image.getThumbnailUrl()
        );
    }
}
