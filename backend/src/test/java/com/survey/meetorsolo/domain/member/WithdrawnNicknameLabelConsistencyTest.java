package com.survey.meetorsolo.domain.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.member.entity.Member;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 탈퇴 회원의 표시 문구가 조회 SQL과 어긋나지 않게 고정한다({@code docs/19} 4.4).
 *
 * <p>탈퇴는 닉네임 컬럼을 {@code NULL}로 지우고, 표시 문구는 {@code members}를 join하는 조회
 * SQL이 {@code status = 'WITHDRAWN'}일 때 만들어 낸다. 문구가 SQL 리터럴로 여러 곳에 흩어져
 * 있으므로 {@link Member#WITHDRAWN_NICKNAME}만 바꾸면 <b>화면은 조용히 옛 문구를 계속
 * 보여준다.</b> 그 어긋남을 여기서 잡는다.
 *
 * <p>새로 {@code members}를 join해 닉네임을 읽는 경로를 만들면 이 목록에 추가한다. 목록에
 * 없다는 것은 치환을 빠뜨렸다는 뜻이기도 하다.
 *
 * <p>{@code ContentCommentRepository}는 일부러 목록에 없다 — 탈퇴가 작성 댓글을
 * {@code DELETED}로 내리고 목록은 {@code VISIBLE}만 조회하므로 탈퇴 회원이 결과에 들어오지
 * 않는다. 자세한 근거는 그 파일의 javadoc에 있다.
 */
class WithdrawnNicknameLabelConsistencyTest {

    /** 파일별로 문구가 몇 번 나와야 하는지. 값은 그 파일에서 치환하는 쿼리 수다. */
    private static final Map<String, Integer> EXPECTED = new LinkedHashMap<>();

    static {
        // 활성 그룹 멤버, 완료 그룹 멤버, 매칭 기록
        EXPECTED.put("domain/matching/repository/MatchGroupMemberRepository.java", 3);
        // 매칭방 이벤트 actor
        EXPECTED.put("domain/matching/repository/MatchEventRepository.java", 1);
        // 차단 목록
        EXPECTED.put("domain/safety/block/repository/MemberBlockRepository.java", 1);
        // 관리자 신고 목록/상세 — 신고자와 피신고자
        EXPECTED.put("domain/safety/report/admin/repository/AdminReportRepository.java", 2);
        // 관리자 안전 알림
        EXPECTED.put("domain/admin/safety/repository/AdminSafetyAlertRepository.java", 1);
        // 관리자 회원 목록/상세가 공유하는 SELECT
        EXPECTED.put("domain/admin/member/repository/AdminMemberRepository.java", 1);
    }

    private static final Path SOURCE_ROOT =
            Path.of("src", "main", "java", "com", "survey", "meetorsolo");

    @Test
    void 조회_SQL의_표시_문구는_상수와_일치한다() {
        String literal = "'" + Member.WITHDRAWN_NICKNAME + "'";

        EXPECTED.forEach((relativePath, expectedCount) -> {
            String source = read(SOURCE_ROOT.resolve(relativePath));
            assertThat(countOccurrences(source, literal))
                    .as("%s의 표시 문구 %s 사용 횟수. 상수만 바꾸면 화면이 옛 문구를 계속 보여준다.",
                            relativePath, literal)
                    .isEqualTo(expectedCount);
        });
    }

    /**
     * 치환은 반드시 {@code status = 'WITHDRAWN'} 조건과 함께 있어야 한다.
     * 조건 없이 문구만 넣으면 살아 있는 회원의 닉네임까지 덮인다.
     */
    @Test
    void 치환은_탈퇴_상태_조건과_함께_있다() {
        EXPECTED.keySet().forEach(relativePath -> {
            String source = read(SOURCE_ROOT.resolve(relativePath));
            assertThat(countOccurrences(source, "'" + Member.WITHDRAWN_NICKNAME + "'"))
                    .as("%s", relativePath)
                    .isEqualTo(countOccurrences(source, "status = '" + Member.STATUS_WITHDRAWN + "'"));
        });
    }

    /** 마이그레이션도 같은 문구를 써야 한다. 다르면 정리 UPDATE와 CHECK가 헛돈다. */
    @Test
    void V30_마이그레이션도_같은_문구를_쓴다() {
        String migration = read(Path.of("src", "main", "resources", "db", "migration",
                "V30__store_null_nickname_for_withdrawn_member.sql"));

        // 근거 주석 1회, 살아 있는 회원 정리 UPDATE 1회, CHECK 제약 1회.
        assertThat(countOccurrences(migration, "'" + Member.WITHDRAWN_NICKNAME + "'")).isEqualTo(3);
        assertThat(migration).contains("chk_members_nickname_not_withdrawn_label");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "소스 파일을 읽지 못했습니다: " + path.toAbsolutePath(), exception);
        }
    }

    private static int countOccurrences(String source, String token) {
        int count = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            count++;
            index = source.indexOf(token, index + token.length());
        }
        return count;
    }
}
