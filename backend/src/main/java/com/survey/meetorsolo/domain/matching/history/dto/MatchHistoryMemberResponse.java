package com.survey.meetorsolo.domain.matching.history.dto;

/**
 * 매칭 기록에 함께 표시하는 상대 참가자다.
 *
 * <p>{@code reported}는 요청한 회원이 이 만남에서 이 상대를 신고한 적이 있는지다. 신고한
 * 사실은 신고자 본인에게만 보이며 상대에게 노출되지 않는다.
 */
public record MatchHistoryMemberResponse(
        long memberId,
        String nickname,
        String profileImageUrl,
        boolean reported
) {
}
