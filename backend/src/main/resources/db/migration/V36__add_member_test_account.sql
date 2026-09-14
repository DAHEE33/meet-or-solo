-- 테스트 계정 표시. GPS 반경 검증을 면제받는 계정을 회원 단위로 지정한다.
--
-- 지금까지는 app.festival.checkin.bypass-radius-check 하나로 local/dev "환경 전체"의 반경
-- 검증을 껐다. 그러면 그 환경에서는 아무도 반경 검증을 통과할 필요가 없어, 정작 검증
-- 로직이 동작하는지 확인할 방법이 사라진다. 계정 단위로 바꾸면 같은 환경에서
-- "테스트 계정은 통과, 일반 계정은 반경 검증"을 동시에 확인할 수 있다.
--
-- role을 쓰지 않는 이유: 관리자인 것과 테스트 계정인 것은 다른 사실이다. role로 겸하면
-- 관리자 계정이 전부 위치 검증 면제가 되고, 반대로 테스트 계정에 관리자 권한이 붙는다.
ALTER TABLE members
    ADD COLUMN test_account BOOLEAN NOT NULL DEFAULT FALSE;

-- 테스트 계정은 소수이므로 partial index로 둔다. 전체 index는 FALSE가 대부분이라 의미가 없다.
CREATE INDEX idx_members_test_account ON members (id) WHERE test_account;

-- 테스트 계정 지정·해제를 감사 로그에 남기려면 action_type 목록에 추가해야 한다.
-- V20(UNSUSPEND), V28(FORCED_WITHDRAWAL), V33(MANNER_TEMPERATURE_ADJUST)과 같은 방식이다.
--
-- 제재 action들과 합치지 않는다. 제재는 회원의 권한을 줄이고 이 조치는 위치 검증이라는
-- 안전장치를 면제해 준다. 감사 로그에서 둘을 섞으면 "무엇을 풀어줬는지"를 찾을 수 없다.
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
        'MANNER_TEMPERATURE_ADJUST',
        'TEST_ACCOUNT_UPDATE',
        'REPORT_RESOLVE',
        'REPORT_REJECT',
        'MANUAL_PENALTY',
        'DATA_CORRECTION'
    ));
