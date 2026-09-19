package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository.ActiveGroupMemberProjection;
import com.survey.meetorsolo.domain.member.service.MemberProfileImageUrls;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record MatchGroupMemberResponse(
        Long memberId,
        String nickname,
        String profileImageUrl,
        String status,
        Integer arrivalMinutes,
        OffsetDateTime arrivalTimeSelectedAt,
        OffsetDateTime arrivedAt
) {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    public MatchGroupMemberResponse(
            Long memberId,
            String nickname,
            String profileImageUrl,
            String status,
            Integer arrivalMinutes,
            OffsetDateTime arrivalTimeSelectedAt
    ) {
        this(
                memberId,
                nickname,
                profileImageUrl,
                status,
                arrivalMinutes,
                arrivalTimeSelectedAt,
                null
        );
    }

    /**
     * 같은 그룹 회원에게 보여줄 카드.
     *
     * <p>프로필 사진은 소셜 URL과 직접 올린 사진 두 갈래인데 예전에는 소셜 쪽만 읽었다.
     * 그래서 <b>사진을 올려도 매칭 상대에게는 보이지 않았고</b>, 소셜 가입자가 사진을 새로
     * 올리면 가입 당시 사진이 계속 보였다. 판정은 {@link MemberProfileImageUrls}가 한다.
     */
    public static MatchGroupMemberResponse from(ActiveGroupMemberProjection member) {
        return new MatchGroupMemberResponse(
                member.getMemberId(),
                member.getNickname(),
                MemberProfileImageUrls.forOtherMember(
                        member.getMemberId(),
                        member.getProfileImageUrl(),
                        member.getProfileImageObjectKey()),
                member.getStatus(),
                member.getArrivalMinutes(),
                member.getArrivalTimeSelectedAt() == null
                        ? null
                        : member.getArrivalTimeSelectedAt().atZone(KOREA_ZONE).toOffsetDateTime(),
                member.getArrivedAt() == null
                        ? null
                        : member.getArrivedAt().atZone(KOREA_ZONE).toOffsetDateTime()
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
