package com.survey.meetorsolo.domain.notification.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 행위자 제외 판정({@code docs/31} 5절 "알림 자기 반향").
 *
 * <p>이 판정은 WebSocket 발송·알림함 저장·Web Push 세 곳이 함께 쓴다. 한 곳만 규칙이 달라지면
 * "토스트로는 떴는데 알림함에는 없다" 같은 상태가 생기므로, 분류 자체를 여기서 고정한다.
 */
class NotificationPolicyTest {

    private static final long ACTOR = 1L;
    private static final long OTHER = 2L;

    /**
     * 문구가 "상대가 ~했어요"인 사유들이다. 그 일을 한 본인에게 보내면 문장이 성립하지 않는다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "MATCH_ACCEPTED",
            "ARRIVAL_TIME_SELECTED",
            "MEMBER_ARRIVED",
            "MEMBER_CANCELLED",
            "MEMBER_LEFT",
            "MEMBER_NO_SHOW"
    })
    void 관측자_시점_사유는_행위자에게_보내지_않는다(String reason) {
        assertThat(NotificationPolicy.deliverableTo(reason, ACTOR, ACTOR)).isFalse();
        assertThat(NotificationPolicy.deliverableTo(reason, ACTOR, OTHER)).isTrue();
    }

    /**
     * 그룹 전체의 사실은 행위자가 가장 먼저 알아야 한다.
     *
     * <p>특히 두 건이 중요하다. {@code MATCH_CONFIRMED}의 행위자는 <b>마지막으로 수락한
     * 사람</b>이고, {@code MATCH_TIMEOUT}의 행위자는 <b>시간 초과로 페널티를 받은 사람</b>이다
     * ({@code MatchProposalResponseService.timeoutAttempt}). 행위자를 무조건 걸러내던 때는
     * 정작 당사자만 알림을 못 받았다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "MATCH_PROPOSED",
            "MATCH_CONFIRMED",
            "MATCH_REJECTED",
            "MATCH_TIMEOUT",
            "MATCH_INSUFFICIENT_MEMBERS",
            "MATCH_CANCELLED",
            "MATCH_COMPLETED",
            "ALL_ARRIVED"
    })
    void 그룹_사실은_행위자에게도_보낸다(String reason) {
        assertThat(NotificationPolicy.deliverableTo(reason, ACTOR, ACTOR)).isTrue();
        assertThat(NotificationPolicy.deliverableTo(reason, ACTOR, OTHER)).isTrue();
    }

    /** 스케줄러가 만든 변화는 행위자가 없다. 아무도 걸러지지 않는다. */
    @Test
    void 행위자가_없으면_전원에게_보낸다() {
        assertThat(NotificationPolicy.deliverableTo("MEMBER_NO_SHOW", null, ACTOR)).isTrue();
        assertThat(NotificationPolicy.deliverableTo("MATCH_COMPLETED", null, OTHER)).isTrue();
    }

    /**
     * 모르는 사유는 보낸다.
     *
     * <p>사유가 하나 늘었을 때 알림이 조용히 사라지는 쪽보다, 본인에게 한 번 더 뜨는 쪽이
     * 추적하기 쉽다. 프론트도 같은 방향이다({@code notificationMessages.ts}의 {@code FALLBACK}).
     */
    @Test
    void 모르는_사유는_걸러내지_않는다() {
        assertThat(NotificationPolicy.deliverableTo("SOMETHING_NEW", ACTOR, ACTOR)).isTrue();
    }
}
