package com.survey.meetorsolo.domain.festival.event;

import java.time.OffsetDateTime;

/**
 * 회원의 기존 ACTIVE 체크인이 새 체크인으로 인해 취소됐음을 알리는 이벤트.
 * matching 도메인이 이 이벤트를 구독해, 취소된 축제에 남아있는 활성 match_pool을 정리한다.
 * ({@code docs/21_CHECKIN_MATCH_POOL_INTEGRATION_DESIGN.md} 참고)
 */
public record FestivalCheckinCancelledEvent(
        long memberId,
        long festivalId,
        OffsetDateTime cancelledAt
) {
}
