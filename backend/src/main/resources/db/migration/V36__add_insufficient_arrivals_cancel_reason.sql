-- docs/19 4.11.2 (V36): 만남 시간이 끝났을 때 도착자가 최소 인원에 못 미치면 그룹을 취소한다.
--
-- 기존 두 사유와 뜻이 다르다.
--   INSUFFICIENT_ACTIVE_MEMBERS : 취소·노쇼로 남은 "활성 구성원"이 부족해 방을 유지할 수 없음
--   MINIMUM_TWO_NOT_ALLOWED     : 2인 진행에 동의하지 않은 구성원이 있음
--   INSUFFICIENT_ARRIVALS       : 방은 유지됐지만 만남 장소에 실제로 "도착한" 사람이 부족함
--
-- 세 가지를 한 사유로 묶으면 나중에 "왜 이 만남이 성사되지 않았나"를 구분할 수 없다.

ALTER TABLE match_groups
    DROP CONSTRAINT chk_match_groups_cancel_reason;

ALTER TABLE match_groups
    ADD CONSTRAINT chk_match_groups_cancel_reason
        CHECK (
            cancel_reason IS NULL
            OR cancel_reason IN (
                'INSUFFICIENT_ACTIVE_MEMBERS',
                'MINIMUM_TWO_NOT_ALLOWED',
                'INSUFFICIENT_ARRIVALS'
            )
        );

-- 번호 주의: 저장소 파일 목록상으로는 V35가 비어 있었지만, 공유 dev DB의
-- flyway_schema_history에는 아직 push되지 않은 V35__add_content_engagement_count_indexes.sql이
-- 2026-09-13에 이미 적용돼 있었다. docs/10 [사고 기록]의 사고 1과 같은 상황이라
-- flyway repair 대신 번호를 V36으로 옮겼다.
