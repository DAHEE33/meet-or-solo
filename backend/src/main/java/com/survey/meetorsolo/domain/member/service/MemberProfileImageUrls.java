package com.survey.meetorsolo.domain.member.service;

/**
 * 회원 프로필 이미지를 어떤 URL로 내려줄지 한 곳에서 정한다.
 *
 * <p>회원은 사진을 두 갈래로 갖는다.
 *
 * <ul>
 *   <li>{@code members.profile_image_url} — 카카오·네이버가 준 <b>외부 URL</b>. 브라우저가
 *       그대로 불러올 수 있다.</li>
 *   <li>{@code members.profile_image_object_key} — <b>직접 올린 사진</b>. private bucket에 있어
 *       우리 서버를 거쳐야만 꺼낼 수 있다.</li>
 * </ul>
 *
 * <p><b>둘을 함께 보는 자리가 한 곳도 없었던 것이 결함의 원인이다.</b> 댓글과 매칭방은
 * {@code profile_image_url}만 읽었기 때문에, 사진을 직접 올린 회원은 남에게 사진이 아예
 * 보이지 않았다(올리기 전 소셜 사진이 남아 있으면 그 옛 사진이 보였다). 올린 사진을 꺼내는
 * 경로도 {@code GET /api/members/me/profile-image} 본인 전용 하나뿐이었다.
 *
 * <p>직접 올린 사진은 {@code GET /api/members/{memberId}/profile-image}로 내려준다. 그 경로는
 * <b>로그인한 회원만</b> 호출할 수 있다({@code MemberProfileImageController}). 비로그인 화면에는
 * 이 값을 아예 담지 않는다 — 부르는 쪽이 책임진다.
 */
public final class MemberProfileImageUrls {

    private MemberProfileImageUrls() {
    }

    /**
     * 다른 회원에게 보여줄 프로필 이미지 URL. 사진이 없으면 {@code null}이다.
     *
     * @param profileImageObjectKey 직접 올린 사진의 object key. 있으면 이쪽이 우선이다 —
     *                              올린 사진이 소셜 사진보다 나중에 정한 값이다
     */
    public static String forOtherMember(
            long memberId, String profileImageUrl, String profileImageObjectKey) {
        if (!isBlank(profileImageObjectKey)) {
            return "/api/members/" + memberId + "/profile-image";
        }
        return isBlank(profileImageUrl) ? null : profileImageUrl.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
