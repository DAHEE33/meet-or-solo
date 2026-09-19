package com.survey.meetorsolo.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 프로필 사진 URL 결정({@code docs/27} 6.2).
 *
 * <p>회원이 사진을 두 갈래로 갖는데(소셜 외부 URL / 직접 올린 object key) 두 값을 함께 보는
 * 자리가 없었던 것이 "사진을 등록해도 댓글에 안 보인다"의 원인이었다. 그 판정을 고정한다.
 */
class MemberProfileImageUrlsTest {

    private static final long MEMBER_ID = 7L;

    /** 직접 올린 사진은 private bucket에 있어 우리 서버를 거치는 경로로만 꺼낼 수 있다. */
    @Test
    void 직접_올린_사진은_서버_경로로_내려준다() {
        assertThat(MemberProfileImageUrls.forOtherMember(MEMBER_ID, null, "profiles/dev/7.jpg"))
                .isEqualTo("/api/members/7/profile-image");
    }

    /**
     * 올린 사진이 소셜 사진을 이긴다.
     *
     * <p>소셜로 가입한 회원이 사진을 새로 올리면 {@code profile_image_url}에는 가입 당시
     * 소셜 사진이 그대로 남는다. 그쪽을 먼저 보면 <b>방금 올린 사진 대신 옛 소셜 사진</b>이
     * 계속 보인다.
     */
    @Test
    void 올린_사진이_소셜_사진보다_우선이다() {
        assertThat(MemberProfileImageUrls.forOtherMember(
                MEMBER_ID, "https://cdn.kakao.com/old.jpg", "profiles/dev/7.jpg"))
                .isEqualTo("/api/members/7/profile-image");
    }

    @Test
    void 올린_사진이_없으면_소셜_사진을_그대로_쓴다() {
        assertThat(MemberProfileImageUrls.forOtherMember(
                MEMBER_ID, "https://cdn.kakao.com/a.jpg", null))
                .isEqualTo("https://cdn.kakao.com/a.jpg");
        assertThat(MemberProfileImageUrls.forOtherMember(
                MEMBER_ID, "  https://cdn.kakao.com/a.jpg  ", "  "))
                .isEqualTo("https://cdn.kakao.com/a.jpg");
    }

    /** 사진이 하나도 없으면 null이다. 화면은 이때 닉네임 이니셜 아바타를 그린다. */
    @Test
    void 사진이_없으면_null이다() {
        assertThat(MemberProfileImageUrls.forOtherMember(MEMBER_ID, null, null)).isNull();
        assertThat(MemberProfileImageUrls.forOtherMember(MEMBER_ID, "", "")).isNull();
        assertThat(MemberProfileImageUrls.forOtherMember(MEMBER_ID, "   ", null)).isNull();
    }
}
