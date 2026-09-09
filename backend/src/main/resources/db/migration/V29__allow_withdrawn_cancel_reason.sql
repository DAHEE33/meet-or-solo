-- 탈퇴로 인한 그룹 이탈 사유를 허용한다(docs/19 4.4).
--
-- V14의 chk_match_group_members_cancel_reason은 사용자가 직접 고르는 취소 사유
-- (MatchCancellationReason enum: SCHEDULE_CHANGED / TRANSPORTATION_ISSUE / OTHER)만 허용했다.
-- 탈퇴는 사용자가 고른 사유가 아니라 계정 삭제의 부수 효과이므로 그 목록에 없었고,
-- 그룹에 속한 회원이 탈퇴하면 이 제약 위반으로 탈퇴 자체가 실패했다.
--
-- 'OTHER'로 뭉개지 않고 별도 값을 둔다. 감사 이력에서 "본인이 사정상 취소"와
-- "계정이 사라져 이탈"을 구분할 수 없으면 노쇼·패널티 분석이 흐려진다.
--
-- V28에 넣지 않고 새 번호로 분리한 이유: V28이 이미 공유 dev DB에 적용되어
-- 수정하면 Flyway checksum 검증이 깨진다.

ALTER TABLE match_group_members
    DROP CONSTRAINT chk_match_group_members_cancel_reason;

ALTER TABLE match_group_members
    ADD CONSTRAINT chk_match_group_members_cancel_reason
        CHECK (
            cancel_reason IS NULL
            OR cancel_reason IN (
                'SCHEDULE_CHANGED',
                'TRANSPORTATION_ISSUE',
                'OTHER',
                'WITHDRAWN'
            )
        );
