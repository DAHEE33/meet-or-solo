-- 탈퇴 회원의 닉네임을 컬럼에 저장하지 않는다(docs/19 4.4).
--
-- V28은 탈퇴 회원의 nickname을 표시용 고정 문구('탈퇴한 회원')로 덮었다. 조회 경로를
-- 건드리지 않고 표시가 맞아떨어진다는 이유였지만, 문구를 컬럼에 두면 두 가지가 깨진다.
--
--   1. 재가입한 계정이 그 문구를 그대로 들고 살아난다.
--      Member.updateSocialProfile은 소셜 로그인 때 닉네임을 채우지만, OAuth가 닉네임을
--      주지 않으면(카카오는 닉네임 제공이 선택 동의다) 채울 값이 없어 문구가 남는다.
--      살아 있는 계정이 댓글·매칭 기록·차단 목록에서 탈퇴한 것처럼 보인다.
--   2. 표시 문구가 곧 "익명화됐는지"를 뜻하는 상태 flag가 된다.
--      문구를 바꾸는 순간 기존 행이 판정에서 빠져 영구히 복구되지 않는다.
--
-- 그래서 컬럼에는 NULL을 저장하고, 표시 문구는 조회 SQL이 status = 'WITHDRAWN'일 때
-- 만들어 낸다(MatchGroupMemberRepository, MatchEventRepository, MemberBlockRepository,
-- AdminReportRepository, AdminSafetyAlertRepository, AdminMemberRepository).
--
-- 응답 DTO와 프론트엔드는 바꾸지 않는다. 프론트 4곳이 nickname.slice(0, 1)로 첫 글자를
-- 뽑으므로(ContentCommentItem, BlockedMembersPage, MatchHistoryPage, MatchingConditionPage)
-- null을 내려보내면 빈 칸이 아니라 렌더링이 죽는다. 치환은 서버에서 끝낸다.
--
-- V28을 고치지 않고 새 번호로 분리한 이유: V28이 이미 공유 dev DB에 적용되어 수정하면
-- Flyway checksum 검증이 깨진다(V29와 같은 사정).

-- 제약을 걸기 전에 기존 데이터를 정리한다.

-- 1. V28이 넣은 탈퇴 회원의 문구를 되돌린다.
UPDATE members
   SET nickname = NULL
 WHERE status = 'WITHDRAWN'
   AND nickname IS NOT NULL;

-- 2. 살아 있는 회원에 남은 문구를 지운다.
--    재가입했지만 OAuth 닉네임이 없어 문구가 남은 계정이 여기서 정리된다.
--    NULL로 두면 다음 소셜 로그인의 updateSocialProfile이 OAuth 닉네임으로 채우고,
--    채울 값이 없어도 정지 중에도 열리는 프로필 수정 화면에서 본인이 입력할 수 있다.
UPDATE members
   SET nickname = NULL
 WHERE status <> 'WITHDRAWN'
   AND nickname = '탈퇴한 회원';

-- 탈퇴 회원의 익명화 검사에 nickname을 포함시킨다.
-- V28은 "표시용 고정 문구로 덮으므로 NULL 검사 대상이 아니다"라며 예외로 뒀다.
ALTER TABLE members
    DROP CONSTRAINT chk_members_withdrawn_anonymized;

ALTER TABLE members
    ADD CONSTRAINT chk_members_withdrawn_anonymized CHECK (
        status <> 'WITHDRAWN'
        OR (nickname IS NULL
            AND email IS NULL
            AND intro IS NULL
            AND profile_image_url IS NULL
            AND profile_image_object_key IS NULL
            AND gender_encrypted IS NULL
            AND age_range_encrypted IS NULL)
    );

-- 어떤 상태에서도 표시 문구를 닉네임 컬럼에 저장할 수 없다.
--
-- 위의 chk_members_withdrawn_anonymized는 탈퇴 회원만 본다. 이 제약은 살아 있는 회원까지
-- 함께 막아 두 경로를 동시에 닫는다.
--   - 탈퇴 회원: 문구가 컬럼으로 되돌아오는 회귀
--   - 살아 있는 회원: 이 문구를 닉네임으로 골라 탈퇴한 회원으로 위장
-- 위장은 MemberProfileService.requireSelectableNickname이 앞단에서 400으로 막지만,
-- 그 검사를 지나가는 새 경로가 생기면 여기서 걸린다.
ALTER TABLE members
    ADD CONSTRAINT chk_members_nickname_not_withdrawn_label CHECK (
        nickname IS NULL OR nickname <> '탈퇴한 회원'
    );
