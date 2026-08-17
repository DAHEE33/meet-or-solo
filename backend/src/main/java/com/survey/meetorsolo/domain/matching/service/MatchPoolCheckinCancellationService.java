package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 다른 축제로 재체크인해 기존 체크인이 취소됐을 때, 그 축제에 남아있는 이 회원의
 * WAITING match_pool을 정리한다. {@code FestivalCheckinCancelledEventHandler}가 이 서비스를
 * 호출한다. LOCKED/PROPOSED 상태의 pool은 이미 매칭 시도가 진행 중일 수 있어 이번 범위에서는
 * 건드리지 않는다({@code docs/21_CHECKIN_MATCH_POOL_INTEGRATION_DESIGN.md} 4.4절 참고).
 */
@Service
public class MatchPoolCheckinCancellationService {

    private final MatchPoolRepository matchPoolRepository;

    public MatchPoolCheckinCancellationService(MatchPoolRepository matchPoolRepository) {
        this.matchPoolRepository = matchPoolRepository;
    }

    @Transactional
    public int cancelWaitingPool(long memberId, long festivalId, OffsetDateTime now) {
        Objects.requireNonNull(now, "now는 필수입니다.");
        return matchPoolRepository.cancelWaitingPool(memberId, festivalId, now);
    }
}
