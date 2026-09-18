-- 관리자 정지 조기 해제(UNSUSPEND) action type 허용
ALTER TABLE admin_actions DROP CONSTRAINT chk_admin_actions_type;
ALTER TABLE admin_actions ADD CONSTRAINT chk_admin_actions_type CHECK (action_type IN (
    'WARNING',
    'SUSPEND',
    'BAN',
    'UNBAN',
    'UNSUSPEND',
    'REPORT_RESOLVE',
    'REPORT_REJECT',
    'MANUAL_PENALTY',
    'DATA_CORRECTION'
));
