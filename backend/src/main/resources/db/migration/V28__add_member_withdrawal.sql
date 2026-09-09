-- 회원 탈퇴(docs/19 4.4)와 관리자 강제 탈퇴, 7일 재가입 쿨오프를 위한 탈퇴 스냅샷.
--
-- 왜 기존 제재 컬럼을 재사용하지 않는가:
--   V19의 chk_members_suspension_period는 status <> 'SUSPENDED'이면 suspended_at과
--   suspended_until을 NULL로 강제하고, V27의 chk_members_sanction_reason_presence도
--   제재 상태가 아니면 사유를 금지한다. 그래서 정지 회원이 탈퇴하면 두 제약 때문에
--   잔여 정지 기간을 그 컬럼에 그대로 둘 수 없다.
--   두 제약을 WITHDRAWN까지 허용하도록 완화하면 정지 해제와 만료 복구의 버그를 잡아온
--   불변식이 함께 헐거워진다. 그래서 탈퇴 시점 값을 별도 스냅샷 컬럼으로 분리한다.
--   V27이 사용자 노출용 사유와 관리자 내부용 사유를 컬럼 수준에서 나눈 것과 같은 방식이다.
--
-- 재가입 판정에 쓰는 값:
--   withdrawn_at              7일 쿨오프 기준 시각
--   withdrawn_rejoin_blocked  영구 재가입 거부 여부. 로그인 경로가 읽는 유일한 값
--   withdrawn_by_admin        관리자 강제 탈퇴인지(사실). 정책이 바뀌어도 이 값은 안 바뀐다
--   withdrawn_suspended_until 잔여 정지 종료 시각. 재가입 시 정지를 이어받는 데 쓴다
--
-- BANNED 회원은 로그인과 /api/** 요청이 모두 막혀 있어(MemberAccessInterceptor)
-- 본인 탈퇴 경로에 도달할 수 없다. withdrawn_from_status = 'BANNED'는 관리자가
-- 영구차단 회원을 강제 탈퇴시킨 경우에만 생긴다.

ALTER TABLE members
    ADD COLUMN withdrawn_from_status VARCHAR(30),
    ADD COLUMN withdrawn_by_admin BOOLEAN,
    ADD COLUMN withdrawn_rejoin_blocked BOOLEAN,
    ADD COLUMN withdrawn_suspended_until TIMESTAMPTZ,
    ADD COLUMN withdrawn_sanction_reason_code VARCHAR(40);

-- 제약을 걸기 전에 기존 데이터를 정리한다.
-- 탈퇴 기능이 없던 시점의 WITHDRAWN row가 dev DB에 남아 있을 수 있다.
UPDATE members
   SET nickname = '탈퇴한 회원',
       email = NULL,
       intro = NULL,
       profile_image_url = NULL,
       profile_image_object_key = NULL,
       gender_encrypted = NULL,
       age_range_encrypted = NULL,
       withdrawn_at = COALESCE(withdrawn_at, updated_at),
       withdrawn_from_status = 'ACTIVE',
       withdrawn_by_admin = FALSE,
       withdrawn_rejoin_blocked = FALSE
 WHERE status = 'WITHDRAWN';

-- 탈퇴 상태가 아닌데 withdrawn_at이 남아 있으면 아래 제약을 위반한다.
UPDATE members
   SET withdrawn_at = NULL
 WHERE status <> 'WITHDRAWN'
   AND withdrawn_at IS NOT NULL;

ALTER TABLE members
    -- 탈퇴면 스냅샷이 반드시 있고, 탈퇴가 아니면 하나도 남아 있으면 안 된다.
    -- 재가입에서 스냅샷을 지우는 것을 잊으면 여기서 걸린다.
    ADD CONSTRAINT chk_members_withdrawal_snapshot CHECK (
        (status = 'WITHDRAWN'
            AND withdrawn_at IS NOT NULL
            AND withdrawn_from_status IS NOT NULL
            AND withdrawn_by_admin IS NOT NULL
            AND withdrawn_rejoin_blocked IS NOT NULL)
        OR
        (status <> 'WITHDRAWN'
            AND withdrawn_at IS NULL
            AND withdrawn_from_status IS NULL
            AND withdrawn_by_admin IS NULL
            AND withdrawn_rejoin_blocked IS NULL
            AND withdrawn_suspended_until IS NULL
            AND withdrawn_sanction_reason_code IS NULL)
    ),
    ADD CONSTRAINT chk_members_withdrawn_from_status CHECK (
        withdrawn_from_status IS NULL OR withdrawn_from_status IN (
            'ACTIVE',
            'PROFILE_REQUIRED',
            'SUSPENDED',
            'BANNED'
        )
    ),
    -- 사용자 노출용 사유 code 목록은 V27의 chk_members_sanction_reason_code와 같아야 한다.
    ADD CONSTRAINT chk_members_withdrawn_sanction_reason_code CHECK (
        withdrawn_sanction_reason_code IS NULL OR withdrawn_sanction_reason_code IN (
            'COMMUNITY_GUIDELINE',
            'HARASSMENT',
            'NO_SHOW_ABUSE',
            'FRAUD_OR_SCAM',
            'SAFETY_RISK',
            'ADMIN_CORRECTION',
            'OTHER'
        )
    ),
    -- 잔여 정지 기간은 정지 중 탈퇴에서만 생기고, 재가입 시 SUSPENDED로 되살리려면
    -- 사유가 함께 있어야 한다(chk_members_sanction_reason_presence가 사유를 요구한다).
    ADD CONSTRAINT chk_members_withdrawn_suspension_snapshot CHECK (
        withdrawn_suspended_until IS NULL
        OR (withdrawn_from_status = 'SUSPENDED'
            AND withdrawn_sanction_reason_code IS NOT NULL)
    ),
    -- 탈퇴 회원에게 개인정보가 남아 있으면 거부한다. 익명화 누락은 코드 리뷰로 놓치기 쉽다.
    -- nickname은 표시용 고정 문구('탈퇴한 회원')로 덮으므로 NULL 검사 대상이 아니다.
    ADD CONSTRAINT chk_members_withdrawn_anonymized CHECK (
        status <> 'WITHDRAWN'
        OR (email IS NULL
            AND intro IS NULL
            AND profile_image_url IS NULL
            AND profile_image_object_key IS NULL
            AND gender_encrypted IS NULL
            AND age_range_encrypted IS NULL)
    );

-- 관리자 강제 탈퇴를 감사 로그에 남기려면 action_type 목록에 추가해야 한다(V20과 같은 방식).
ALTER TABLE admin_actions
    DROP CONSTRAINT chk_admin_actions_type;

ALTER TABLE admin_actions
    ADD CONSTRAINT chk_admin_actions_type CHECK (action_type IN (
        'WARNING',
        'SUSPEND',
        'BAN',
        'UNBAN',
        'UNSUSPEND',
        'FORCED_WITHDRAWAL',
        'REPORT_RESOLVE',
        'REPORT_REJECT',
        'MANUAL_PENALTY',
        'DATA_CORRECTION'
    ));
