package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    /**
     * 호출자 {@code FestivalCheckinCancelledEventHandler}가
     * {@code @TransactionalEventListener(AFTER_COMMIT)}이다. 그 시점에는 원본 트랜잭션이 이미
     * 커밋됐지만 동기화는 살아 있어서, 기본 {@code REQUIRED}로 두면 Spring이 새 트랜잭션을 열지 않고
     * 완료된 트랜잭션에 참여하려다 {@code no transaction is in progress}로 실패한다.
     * pool entry 매칭 경로({@code PoolEntryMatchPoolClaimService} 등)와 동일하게
     * {@code REQUIRES_NEW}로 새 트랜잭션을 연다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cancelWaitingPool(long memberId, long festivalId, OffsetDateTime now) {
        Objects.requireNonNull(now, "now는 필수입니다.");
        return matchPoolRepository.cancelWaitingPool(memberId, festivalId, now);
    }
}
