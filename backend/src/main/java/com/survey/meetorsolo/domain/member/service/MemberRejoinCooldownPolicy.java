package com.survey.meetorsolo.domain.member.service;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 탈퇴 후 재가입 쿨오프({@code docs/19} 4.4).
 *
 * <p>탈퇴 직후 같은 소셜 계정으로 바로 돌아오는 것을 막는다. 쿨오프가 지나면 같은 계정으로
 * 재가입할 수 있고, 그때 계정을 되살려 프로필을 다시 입력받는다.
 *
 * <p><b>환경변수로 빼지 않는다.</b> 정책값이고 환경별로 달라야 할 이유가 없다.
 * {@code MatchReportWindowPolicy.WINDOW_DAYS}, {@code CheckinValidityPolicy.VALIDITY}와 같은
 * 방식이다. 목적이 다른 window와 값을 합치지 않는다.
 *
 * <p><b>제재 면제 장치가 아니다.</b> 정지 중 탈퇴한 회원은 쿨오프가 지나 재가입해도
 * 잔여 정지 기간을 이어받는다({@code Member.rejoin}). 그렇지 않으면 30일 정지가
 * "탈퇴하고 7일 뒤 재가입"으로 23일 세탁된다.
 */
public final class MemberRejoinCooldownPolicy {

    /** 탈퇴 후 재가입이 막히는 기간. */
    public static final Duration COOLDOWN = Duration.ofDays(7);

    private MemberRejoinCooldownPolicy() {
    }

    /** 탈퇴 시각으로부터 재가입이 가능해지는 시각. */
    public static OffsetDateTime rejoinAvailableAt(OffsetDateTime withdrawnAt) {
        if (withdrawnAt == null) {
            throw new IllegalArgumentException("탈퇴 시각이 없으면 재가입 시각을 계산할 수 없습니다.");
        }
        return withdrawnAt.plus(COOLDOWN);
    }

    /** 쿨오프가 아직 남아 있는지. */
    public static boolean isCoolingDown(OffsetDateTime withdrawnAt, OffsetDateTime now) {
        return now.isBefore(rejoinAvailableAt(withdrawnAt));
    }
}
