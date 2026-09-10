-- 관리자 매너온도 수동 조정을 감사 로그에 남기려면 action_type 목록에 추가해야 한다.
-- V20(UNSUSPEND), V28(FORCED_WITHDRAWAL)과 같은 방식으로 CHECK 제약을 교체한다.
--
-- 매너온도는 지금까지 신고 확정으로만 내려가는 하강 전용 지표였다. 관리자 수동 조정이
-- 생기기 전에는 잘못 깎인 온도를 되돌릴 수단이 아예 없었다.
--
-- MANUAL_PENALTY와 합치지 않는다. MANUAL_PENALTY는 penalty_score를 올리는 제재이고
-- 매너온도 조정은 올리는 쪽이 주 용도인 복구 수단이다. 감사 로그에서 둘을 섞으면
-- "관리자가 제재했다"와 "관리자가 복구했다"를 구분할 수 없다.
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
        'REPORT_RESOLVE',
        'REPORT_REJECT',
        'MANUAL_PENALTY',
        'DATA_CORRECTION'
    ));
