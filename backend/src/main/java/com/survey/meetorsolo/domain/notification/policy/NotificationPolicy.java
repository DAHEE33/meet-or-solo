package com.survey.meetorsolo.domain.notification.policy;

import java.time.Duration;
import java.util.Set;

/**
 * 알림함에 무엇을 얼마나 남길지 정한다({@code docs/32} 3.3).
 *
 * <p>정책을 상수로 모아 두는 이유는 저장하는 쪽(이벤트 handler)과 지우는 쪽(보관 정리)이
 * 서로 다른 클래스이기 때문이다. 두 곳에 흩어지면 "무엇이 왜 안 남았는가"를 추적할 수 없다.
 */
public final class NotificationPolicy {

    /**
     * 알림함에 남기는 사유.
     *
     * <p><b>중간 상태는 저장하지 않는다.</b> {@code MEMBER_ARRIVED}·{@code ARRIVAL_TIME_SELECTED}
     * 처럼 만남이 진행되는 동안 여러 번 오는 변화까지 남기면, 나중에 목록을 열었을 때
     * 정작 중요한 "매칭이 확정됐다"가 묻힌다. 그 순간에 필요한 정보는 실시간 토스트가 이미
     * 전달했고, 지나고 나서 다시 볼 값은 아니다.
     *
     * <p>남기는 것은 <b>내가 놓치면 손해가 나거나, 결과가 확정된</b> 사유다.
     */
    private static final Set<String> STORED_REASONS = Set.of(
            // 놓치면 penalty_score +1과 쿨타임 2분이 붙는다. 알림함의 존재 이유에 가장 가깝다.
            "MATCH_PROPOSED",
            "MATCH_CONFIRMED",
            // 아래는 모두 "그 매칭이 어떻게 끝났는가"다.
            "MATCH_REJECTED",
            "MATCH_TIMEOUT",
            "MATCH_INSUFFICIENT_MEMBERS",
            "MATCH_CANCELLED",
            "MATCH_COMPLETED"
    );

    /**
     * 앱이 꺼져 있어도 보내는 사유(Web Push, {@code docs/32} 3.4).
     *
     * <p>알림함보다 좁다. push는 잠금 화면까지 올라오는 가장 시끄러운 경로라, "지금 손을 쓰지
     * 않으면 손해가 나는" 것만 보낸다.
     *
     * <ul>
     *   <li>{@code MATCH_PROPOSED} — 응답 시간 30초. 놓치면 {@code penalty_score +1}에 쿨타임
     *       2분이 붙는다. push가 필요한 가장 큰 이유다.</li>
     *   <li>{@code MATCH_CONFIRMED} — 만남 장소로 이동을 시작해야 하고 도착 마감이 30분이다.</li>
     * </ul>
     *
     * <p>매칭이 끝났다는 알림(거절·시간 초과·취소·완료)은 push로 보내지 않는다. 지금 할 일이
     * 없는 소식이라 앱을 열었을 때 알림함에서 보면 된다.
     */
    private static final Set<String> PUSHED_REASONS = Set.of(
            "MATCH_PROPOSED",
            "MATCH_CONFIRMED"
    );

    /**
     * 문구가 <b>관측자 시점</b>인 사유. 그 변화를 만든 본인에게는 보내지 않는다.
     *
     * <p>내가 도착을 눌렀는데 "상대가 만남 장소에 도착했어요"가 나에게 오는 것이
     * {@code docs/31} 5절의 "알림 자기 반향"이다. 문구를 정하는
     * {@code notificationMessages.ts}가 이 사유들을 전부 "상대가/한 명이 ~했어요"로 쓰고 있어,
     * 행위자에게 보내면 문장 자체가 성립하지 않는다.
     *
     * <p><b>여기 없는 사유는 행위자에게도 보낸다.</b> 나머지는 "매칭이 확정됐어요"처럼
     * 그룹 전체의 사실이고, 그 사실을 만든 사람이야말로 가장 먼저 알아야 한다. 행위자를
     * 무조건 걸러내면 이런 일이 생긴다 —
     *
     * <ul>
     *   <li>{@code MATCH_CONFIRMED} — 행위자는 마지막으로 수락한 사람이다. 걸러내면 매칭을
     *       성사시킨 본인만 확정 알림을 못 받는다.</li>
     *   <li>{@code MATCH_TIMEOUT} — 행위자는 시간 초과된 사람이다
     *       ({@code MatchProposalResponseService.timeoutAttempt}). 걸러내면
     *       {@code penalty_score +1}과 쿨타임 2분을 받은 당사자만 이유를 모른다.</li>
     * </ul>
     */
    private static final Set<String> ACTOR_RELATIVE_REASONS = Set.of(
            "MATCH_ACCEPTED",
            "ARRIVAL_TIME_SELECTED",
            "MEMBER_ARRIVED",
            "MEMBER_CANCELLED",
            "MEMBER_LEFT",
            "MEMBER_NO_SHOW"
    );

    /**
     * 보관 기간과 건수를 <b>둘 다</b> 건다.
     *
     * <p>기간만 걸면 짧은 기간에 몰아 쓰는 회원의 목록이 무한정 길어지고, 건수만 걸면 오래
     * 쓰지 않은 계정에 몇 달 전 알림이 남는다. 둘 중 먼저 걸리는 쪽이 지운다.
     */
    public static final Duration RETENTION = Duration.ofDays(30);

    public static final int RETENTION_COUNT = 100;

    /** 한 번에 내려주는 기본 건수와 상한. 상한은 보관 건수를 넘지 않는다. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final int MAX_PAGE_SIZE = RETENTION_COUNT;

    private NotificationPolicy() {
    }

    public static boolean stored(String reason) {
        return STORED_REASONS.contains(reason);
    }

    public static boolean pushed(String reason) {
        return PUSHED_REASONS.contains(reason);
    }

    /**
     * 이 사유를 이 회원에게 전달해도 되는지.
     *
     * <p><b>세 경로가 모두 이 판정을 쓴다</b> — WebSocket 실시간 발송, 알림함 저장, Web Push.
     * 예전에는 알림함과 push에만 행위자 제외가 있고 WebSocket에는 없어서, 내가 누른 일이
     * 토스트로는 뜨는데 알림함에는 없는 상태가 됐다. 같은 이벤트를 어느 경로로 받느냐에 따라
     * 결과가 달라지면 안 된다.
     *
     * @param actorMemberId 그 변화를 만든 회원. 스케줄러가 만든 변화는 {@code null}
     */
    public static boolean deliverableTo(String reason, Long actorMemberId, long memberId) {
        if (actorMemberId == null || actorMemberId != memberId) return true;
        return !ACTOR_RELATIVE_REASONS.contains(reason);
    }
}
