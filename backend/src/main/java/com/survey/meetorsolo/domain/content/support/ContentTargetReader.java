package com.survey.meetorsolo.domain.content.support;

import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import com.survey.meetorsolo.domain.tourplace.repository.TourPlaceRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 찜·댓글 대상이 실제로 존재하고 공개 대상인지 검증한다.
 *
 * <p>{@code HIDDEN}만 막는다. 지난 축제({@code ENDED})와 동기화상 사라진
 * 대상({@code INACTIVE})은 허용한다 — 사용자가 명시적으로 저장한 항목이 동기화 사정으로 사라지면
 * 안 된다(docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 5.1).
 */
@Component
public class ContentTargetReader {

    private final FestivalRepository festivals;
    private final TourPlaceRepository tourPlaces;

    public ContentTargetReader(FestivalRepository festivals, TourPlaceRepository tourPlaces) {
        this.festivals = festivals;
        this.tourPlaces = tourPlaces;
    }

    /** 대상이 없거나 {@code HIDDEN}이면 {@code NOT_FOUND}를 던진다. */
    @Transactional(readOnly = true)
    public void requireVisible(ContentTarget target) {
        boolean visible = target.isFestival()
                ? festivals.findById(target.id())
                        .filter(festival -> festival.getStatus() != FestivalStatus.HIDDEN)
                        .isPresent()
                : tourPlaces.findById(target.id())
                        .filter(place -> place.getStatus() != TourPlaceStatus.HIDDEN)
                        .isPresent();
        if (!visible) {
            throw new BusinessException(ErrorCode.CONTENT_TARGET_NOT_FOUND);
        }
    }
}
