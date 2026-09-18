-- 회원 제재 사유·기간 통보(docs/19 4.8)를 위해 사용자 노출용 사유 code를 members에 둔다.
--
-- admin_actions.reason_code를 매번 조회하지 않고 members에 denormalize하는 이유:
--   1. MemberAccessPolicy는 member 도메인이라 admin repository를 참조하면 계층이 역전된다.
--   2. 제재 판정은 이미 읽어온 Member 하나로 끝나야 하므로 403 경로에 추가 query를 두지 않는다.
--   3. admin_actions.reason은 관리자 자유 입력 note라 사용자에게 노출할 수 없다.
--      사용자 노출용(sanction_reason_code)과 관리자 내부용(reason)을 컬럼 수준에서 분리한다.

ALTER TABLE members
    ADD COLUMN sanction_reason_code VARCHAR(40);

-- 제약을 걸기 전에 기존 제재 회원을 backfill한다.
-- 가장 최근 SUSPEND/BAN 조치의 reason_code를 쓰고, 조치 이력이 없으면 OTHER로 둔다.
UPDATE members m
   SET sanction_reason_code = COALESCE(
           (SELECT a.reason_code
              FROM admin_actions a
             WHERE a.target_member_id = m.id
               AND a.action_type IN ('SUSPEND', 'BAN')
               AND a.reason_code IS NOT NULL
             ORDER BY a.created_at DESC, a.id DESC
             LIMIT 1),
           'OTHER'
       )
 WHERE m.status IN ('SUSPENDED', 'BANNED')
   AND m.sanction_reason_code IS NULL;

ALTER TABLE members
    ADD CONSTRAINT chk_members_sanction_reason_code CHECK (
        sanction_reason_code IS NULL OR sanction_reason_code IN (
            'COMMUNITY_GUIDELINE',
            'HARASSMENT',
            'NO_SHOW_ABUSE',
            'FRAUD_OR_SCAM',
            'SAFETY_RISK',
            'ADMIN_CORRECTION',
            'OTHER'
        )
    ),
    -- 제재 상태면 사유가 반드시 있고, 제재 상태가 아니면 사유가 남아 있으면 안 된다.
    -- 정지 해제·만료 복구에서 사유를 지우는 것을 잊으면 여기서 걸린다.
    ADD CONSTRAINT chk_members_sanction_reason_presence CHECK (
        (status IN ('SUSPENDED', 'BANNED') AND sanction_reason_code IS NOT NULL)
        OR
        (status NOT IN ('SUSPENDED', 'BANNED') AND sanction_reason_code IS NULL)
    );
