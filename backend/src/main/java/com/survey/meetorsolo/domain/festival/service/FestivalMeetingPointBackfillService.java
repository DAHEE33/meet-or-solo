package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPoint;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPointStatus;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalMeetingPointRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만남 장소가 하나도 등록되지 않은 ACTIVE 축제에 축제 좌표 기반 기본 장소 1건을 채워 넣는다.
 *
 * <p>기준은 "행이 0건"이며 "ACTIVE 상태 행이 0건"이 아니다. 관리자가 이미 장소를 등록해뒀다면
 * (전부 INACTIVE라도) 그 축제는 다시 대상으로 잡지 않는다 — 관리자가 조정한 값을 스케줄러가
 * 대신 복구하거나 덮어쓰지 않는다는 원칙을 지키기 위함이다. 자세한 설계는
 * {@code docs/24_ADMIN_MEETING_POINT_MANAGEMENT_DESIGN.md} 3장을 참고한다.
 */
@Service
public class FestivalMeetingPointBackfillService {

    private static final Logger log = LoggerFactory.getLogger(FestivalMeetingPointBackfillService.class);
    private static final int DEFAULT_ASSIGNMENT_ORDER = 0;
    private static final String KAKAO_PLACE_ID_PREFIX = "AUTO-";
    private static final String DEFAULT_NAME_SUFFIX = " (자동 등록 기본 위치)";
    private static final String DEFAULT_ADDRESS_PLACEHOLDER = "주소 미확인 (관리자 확인 필요)";
    private static final int NAME_MAX_LENGTH = 255; // chk 제약은 없지만 festival_meeting_points.name은 VARCHAR(255)

    private final FestivalRepository festivals;
    private final FestivalMeetingPointRepository points;

    public FestivalMeetingPointBackfillService(FestivalRepository festivals,
            FestivalMeetingPointRepository points) {
        this.festivals = festivals;
        this.points = points;
    }

    @Transactional
    public int seedMissingDefaultPoints() {
        List<Festival> targets = festivals.findAllByStatusWithoutMeetingPoint(FestivalStatus.ACTIVE);
        int seeded = 0;
        for (Festival festival : targets) {
            if (festival.getMapX() == null || festival.getMapY() == null) {
                log.warn("만남 장소 자동 백필 skip: 좌표 없음. festivalId={}, contentId={}",
                        festival.getId(), festival.getContentId());
                continue;
            }
            FestivalMeetingPoint point = FestivalMeetingPoint.inactive(
                    festival.getId(),
                    KAKAO_PLACE_ID_PREFIX + festival.getContentId(),
                    defaultName(festival),
                    defaultAddress(festival),
                    festival.getMapX(),
                    festival.getMapY(),
                    DEFAULT_ASSIGNMENT_ORDER);
            point.changeStatus(FestivalMeetingPointStatus.ACTIVE);
            points.save(point);
            seeded++;
        }
        return seeded;
    }

    private String defaultName(Festival festival) {
        String name = festival.getTitle() + DEFAULT_NAME_SUFFIX;
        if (name.length() <= NAME_MAX_LENGTH) {
            return name;
        }
        int titleLimit = NAME_MAX_LENGTH - DEFAULT_NAME_SUFFIX.length();
        return festival.getTitle().substring(0, titleLimit) + DEFAULT_NAME_SUFFIX;
    }

    private String defaultAddress(Festival festival) {
        String address = festival.getAddress();
        return address == null || address.isBlank() ? DEFAULT_ADDRESS_PLACEHOLDER : address;
    }
}
