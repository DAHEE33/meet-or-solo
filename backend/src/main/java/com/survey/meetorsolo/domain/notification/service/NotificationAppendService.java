package com.survey.meetorsolo.domain.notification.service;

import com.survey.meetorsolo.domain.notification.entity.Notification;
import com.survey.meetorsolo.domain.notification.policy.NotificationPolicy;
import com.survey.meetorsolo.domain.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상태 변화를 알림함에 남긴다({@code docs/32} 3.3).
 *
 * <p><b>실시간 알림과 같은 자리에서 저장하되, 저장 실패가 실시간 알림을 막지 않는다.</b>
 * 호출자는 {@code AFTER_COMMIT} handler이고 여기서 새 transaction을 연다.
 * {@code MannerTemperatureRewardService}가 분리된 것과 같은 이유다 — 알림함은 부가 기능이고,
 * 여기서 실패한다고 이미 끝난 매칭을 되돌릴 수는 없다.
 */
@Service
public class NotificationAppendService {

    private static final Logger log = LoggerFactory.getLogger(NotificationAppendService.class);

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationAppendService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * 수신자마다 한 줄씩 남긴다.
     *
     * <p>관측자 시점 사유는 행위자 본인에게 남기지 않는다. 내가 도착을 눌렀는데 "상대가
     * 도착했어요"가 내 알림함에 남는 것이 1단계의 알려진 한계였다(`docs/31` 5절 "알림 자기
     * 반향"). 판정은 {@link NotificationPolicy#deliverableTo}가 하고 WebSocket·push도 같은
     * 것을 쓴다 — 예전에는 여기서만 행위자를 무조건 걸러서, 매칭을 성사시킨 본인이
     * {@code MATCH_CONFIRMED}를, 시간 초과된 본인이 {@code MATCH_TIMEOUT}을 못 받았다.
     *
     * @param actorMemberId 그 변화를 만든 회원. 스케줄러가 만든 변화는 {@code null}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int append(
            List<Long> memberIds, String reason, Long actorMemberId, OffsetDateTime occurredAt) {
        if (!NotificationPolicy.stored(reason)) return 0;

        OffsetDateTime now = OffsetDateTime.now(clock);
        int saved = 0;
        // deadlock을 피하려고 항상 회원 ID 오름차순으로 쓴다(보상 지급과 같은 규칙).
        for (long memberId : memberIds.stream().distinct().sorted().toList()) {
            if (!NotificationPolicy.deliverableTo(reason, actorMemberId, memberId)) continue;
            if (appendOne(memberId, reason, actorMemberId, occurredAt, now)) saved++;
        }
        return saved;
    }

    private boolean appendOne(
            long memberId, String reason, Long actorMemberId,
            OffsetDateTime occurredAt, OffsetDateTime now) {
        // 같은 알림이 두 번 오는 경로가 있다(이벤트 재발행). unique index가 최종 방어선이고,
        // 여기서 먼저 걸러 예외 로그가 쌓이지 않게 한다.
        if (notifications.existsByMemberIdAndReasonAndOccurredAt(memberId, reason, occurredAt)) {
            return false;
        }
        try {
            notifications.save(Notification.of(memberId, reason, actorMemberId, occurredAt, now));
        } catch (DataIntegrityViolationException duplicated) {
            // 동시에 같은 알림이 들어온 경우다. 중복은 버리면 되는 값이라 실패로 보지 않는다.
            log.debug("중복 알림을 건너뜁니다. memberId={}, reason={}", memberId, reason);
            return false;
        }
        prune(memberId, now);
        return true;
    }

    /**
     * 보관 기간과 건수를 넘은 알림을 지운다.
     *
     * <p>별도 스케줄러를 두지 않고 쓰는 김에 정리한다. 알림이 생기지 않는 계정은 목록도
     * 늘지 않아 정리할 것이 없고, 스케줄러를 하나 더 두면 꺼졌을 때 조용히 쌓인다.
     */
    private void prune(long memberId, OffsetDateTime now) {
        notifications.deleteOlderThan(memberId, now.minus(NotificationPolicy.RETENTION));
        notifications.deleteBeyondNewest(memberId, NotificationPolicy.RETENTION_COUNT);
    }
}
