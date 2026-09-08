package com.survey.meetorsolo.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.admin.member.dto.AdminMemberActionReasonCode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 사용자 노출 문구의 신고자 보호 제약을 못 박는 테스트.
 *
 * <p>제재 사유 문구에 신고 건수·신고 시점·신고자 수를 암시하는 표현이 들어가면 사용자가
 * 자신을 신고한 사람의 범위를 좁힐 수 있다. 문구를 고칠 일이 생기면 여기서 먼저 걸린다.
 * {@code docs/19} 4.8·5장과 {@code docs/05_MATCHING_POLICY.md}의 신고 정책을 따른다.
 */
class MemberSanctionReasonTest {

    /**
     * 신고자 수·시점을 추정할 수 있게 만드는 표현들.
     * "회"·"명" 같은 단위는 "회원"처럼 정상 단어에도 들어가 거짓 양성이 되므로 넣지 않고,
     * 건수 노출은 아래 숫자 검사로 잡는다.
     */
    private static final List<String> FORBIDDEN_WORDS = List.of(
            "신고", "신고자", "누적", "건수", "제보", "고발", "피해자");

    @ParameterizedTest
    @EnumSource(MemberSanctionReason.class)
    void 사용자_노출_문구는_신고를_추정할_수_있는_표현을_쓰지_않는다(MemberSanctionReason reason) {
        String message = reason.getUserMessage();

        assertThat(message).isNotBlank();
        assertThat(FORBIDDEN_WORDS)
                .allSatisfy(word -> assertThat(message)
                        .as("사유 문구에 신고자를 추정할 수 있는 표현이 들어갔다: %s", reason.name())
                        .doesNotContain(word));
    }

    @ParameterizedTest
    @EnumSource(MemberSanctionReason.class)
    void 사용자_노출_문구에는_숫자가_들어가지_않는다(MemberSanctionReason reason) {
        assertThat(reason.getUserMessage())
                .as("숫자는 신고 건수로 읽힐 수 있다: %s", reason.name())
                .doesNotMatch(".*\\d.*");
    }

    /**
     * {@code members.sanction_reason_code}에는 관리자 조치의 reason code가 그대로 저장된다.
     * 두 enum의 값 목록이 갈라지면 저장된 code를 사용자 문구로 바꿀 수 없어 전부 OTHER로 떨어진다.
     * DB의 {@code chk_members_sanction_reason_code}도 같은 목록을 강제한다.
     */
    @Test
    void 관리자_조치_사유_code와_값_목록이_같다() {
        Set<String> userFacing = Arrays.stream(MemberSanctionReason.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        Set<String> adminFacing = Arrays.stream(AdminMemberActionReasonCode.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(userFacing).isEqualTo(adminFacing);
    }

    @Test
    void 알_수_없는_code는_포괄_문구로_떨어뜨린다() {
        assertThat(MemberSanctionReason.from(null)).isEqualTo(MemberSanctionReason.OTHER);
        assertThat(MemberSanctionReason.from("")).isEqualTo(MemberSanctionReason.OTHER);
        assertThat(MemberSanctionReason.from("NOT_A_REASON")).isEqualTo(MemberSanctionReason.OTHER);
    }

    /**
     * 내부 code 이름이 문구로 새어 나가면 {@code NO_SHOW_ABUSE}처럼 정책 용어가 그대로 노출된다.
     */
    @ParameterizedTest
    @EnumSource(MemberSanctionReason.class)
    void 문구에_code_이름을_그대로_쓰지_않는다(MemberSanctionReason reason) {
        assertThat(reason.getUserMessage().toUpperCase(Locale.ROOT))
                .doesNotContain(reason.name());
    }
}
