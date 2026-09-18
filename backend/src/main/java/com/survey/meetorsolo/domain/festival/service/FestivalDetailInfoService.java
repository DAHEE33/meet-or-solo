package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.festival.config.FestivalDetailInfoProperties;
import com.survey.meetorsolo.domain.festival.dto.FestivalDetailInfo;
import com.survey.meetorsolo.domain.festival.dto.FestivalInfoItem;
import com.survey.meetorsolo.domain.festival.dto.FestivalProgramItem;
import com.survey.meetorsolo.external.tourapi.client.TourApiClient;
import com.survey.meetorsolo.external.tourapi.dto.DetailCommonItem;
import com.survey.meetorsolo.external.tourapi.dto.DetailInfoItem;
import com.survey.meetorsolo.external.tourapi.dto.DetailIntroFestivalItem;
import com.survey.meetorsolo.external.tourapi.dto.FestivalDetailApiRequest;
import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import com.survey.meetorsolo.global.text.TextSanitizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 축제 소개글/이용정보/프로그램은 TourAPI detailCommon2/detailIntro2/detailInfo2를 축제 상세
 * 조회 시점에 온디맨드로 호출해 채운다(별도 DB 동기화 없음). 상세 페이지를 열 때마다 호출이
 * 나가면 관광공사 API 일일 호출 한도를 빠르게 소진할 수 있어, contentId 기준 인메모리 TTL
 * 캐시로 같은 축제의 반복 조회를 흡수한다. Redis는 MVP 단계에서 도입하지 않는다.
 */
@Service
public class FestivalDetailInfoService {

    private static final Logger log = LoggerFactory.getLogger(FestivalDetailInfoService.class);

    private final TourApiClient tourApiClient;
    private final FestivalDetailInfoProperties properties;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public FestivalDetailInfoService(TourApiClient tourApiClient, FestivalDetailInfoProperties properties) {
        this.tourApiClient = tourApiClient;
        this.properties = properties;
    }

    public FestivalDetailInfo getDetailInfo(String contentId, String contentTypeId) {
        Instant now = Instant.now();
        CacheEntry cached = cache.get(contentId);
        if (cached != null && cached.isFresh(now, properties.cacheTtl())) {
            return cached.detailInfo();
        }

        FestivalDetailApiRequest request = new FestivalDetailApiRequest(contentId, contentTypeId);
        FestivalDetailInfo detailInfo = new FestivalDetailInfo(
                fetchIntro(request),
                fetchInfoItems(request),
                fetchPrograms(request)
        );

        // TourAPI가 일시적으로 실패해 전부 비어 있으면 캐싱하지 않아 다음 요청에서 다시 시도한다.
        if (!detailInfo.isEmpty()) {
            cache.put(contentId, new CacheEntry(detailInfo, now));
        }
        return detailInfo;
    }

    private String fetchIntro(FestivalDetailApiRequest request) {
        try {
            return tourApiClient.fetchDetailCommon(request)
                    .map(DetailCommonItem::overview)
                    .map(TextSanitizer::stripHtml)
                    .filter(text -> !text.isBlank())
                    .orElse("");
        } catch (TourApiClientException exception) {
            log.warn("축제 소개글 조회 실패. contentId={}, type={}", request.contentId(), exception.getErrorType());
            return "";
        }
    }

    private List<FestivalInfoItem> fetchInfoItems(FestivalDetailApiRequest request) {
        try {
            return tourApiClient.fetchDetailIntroFestival(request)
                    .map(this::toInfoItems)
                    .orElseGet(List::of);
        } catch (TourApiClientException exception) {
            log.warn("축제 이용정보 조회 실패. contentId={}, type={}", request.contentId(), exception.getErrorType());
            return List.of();
        }
    }

    private List<FestivalProgramItem> fetchPrograms(FestivalDetailApiRequest request) {
        try {
            return tourApiClient.fetchDetailInfo(request).stream()
                    .map(this::toProgramItem)
                    .filter(item -> item != null)
                    .toList();
        } catch (TourApiClientException exception) {
            log.warn("축제 프로그램 조회 실패. contentId={}, type={}", request.contentId(), exception.getErrorType());
            return List.of();
        }
    }

    private List<FestivalInfoItem> toInfoItems(DetailIntroFestivalItem item) {
        List<FestivalInfoItem> items = new ArrayList<>();
        addIfPresent(items, "공연 시간", item.playTime());
        addIfPresent(items, "이용 시간", item.useTimeFestival());
        addIfPresent(items, "할인 정보", item.discountInfo());
        addIfPresent(items, "관람 소요시간", item.spendTime());
        addIfPresent(items, "주최", combine(item.sponsor1(), item.sponsor1Tel()));
        addIfPresent(items, "주관", combine(item.sponsor2(), item.sponsor2Tel()));
        addIfPresent(items, "행사 장소", item.eventPlace());
        addIfPresent(items, "관람 연령", item.ageLimit());
        addIfPresent(items, "예매처", item.bookingPlace());
        return items;
    }

    private FestivalProgramItem toProgramItem(DetailInfoItem item) {
        String name = normalize(item.infoName());
        if (name == null) {
            return null;
        }
        String description = TextSanitizer.stripHtml(normalize(item.infoText()));
        return new FestivalProgramItem(name, description == null ? "" : description, "");
    }

    private void addIfPresent(List<FestivalInfoItem> items, String label, String value) {
        String normalized = normalize(value);
        if (normalized != null) {
            items.add(new FestivalInfoItem(label, normalized));
        }
    }

    private String combine(String value, String phone) {
        String normalizedValue = normalize(value);
        String normalizedPhone = normalize(phone);
        if (normalizedValue == null) {
            return null;
        }
        return normalizedPhone == null ? normalizedValue : normalizedValue + " · " + normalizedPhone;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = TextSanitizer.stripHtml(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private record CacheEntry(FestivalDetailInfo detailInfo, Instant fetchedAt) {
        boolean isFresh(Instant now, java.time.Duration ttl) {
            return fetchedAt.plus(ttl).isAfter(now);
        }
    }
}
