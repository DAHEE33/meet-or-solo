package com.survey.meetorsolo.domain.member.service;

import com.survey.meetorsolo.domain.member.dto.MemberSanctionNotice;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;

/**
 * 제재로 접근이 막혔음을 사유·기간과 함께 알리는 예외.
 *
 * <p>{@code ErrorCode}는 기존과 같은 {@code MEMBER_SUSPENDED}/{@code MEMBER_BANNED}를 유지한다.
 * 클라이언트가 보는 code와 HTTP status가 달라지지 않으므로 기존 처리와 호환된다.
 *
 * <p>{@code GlobalExceptionHandler}가 이 예외를 잡아 403 body에 안내를 담고,
 * 같은 응답에 단기 notice cookie를 실어 로그인 화면이 안내를 다시 조회할 수 있게 한다.
 */
public class MemberSanctionException extends BusinessException {

    private final Long memberId;
    private final MemberSanctionNotice notice;

    public MemberSanctionException(ErrorCode errorCode, Long memberId, MemberSanctionNotice notice) {
        super(errorCode);
        this.memberId = memberId;
        this.notice = notice;
    }

    /**
     * 아직 저장되지 않은 회원으로 판정한 경우 {@code null}일 수 있다.
     * id가 없으면 notice cookie를 발급할 수 없으므로 호출부가 확인해야 한다.
     */
    public Long getMemberId() {
        return memberId;
    }

    public MemberSanctionNotice getNotice() {
        return notice;
    }
}
