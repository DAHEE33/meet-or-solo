-- docs/19 4.11.2 (V37): 만남 시간이 끝났을 때 도착자가 최소 인원에 못 미치면 그룹을 취소한다.
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

-- 번호 주의: 이 migration은 V35 -> V36 -> V37로 두 번 옮겼다. 두 번 다 저장소에는 없는
-- migration이 공유 dev DB에 먼저 적용돼 있었기 때문이다.
--   V35__add_content_engagement_count_indexes.sql (2026-09-13 적용, 이후 push됨)
--   V36__add_member_test_account.sql             (2026-09-14 20:31 적용, 저장소에 없음)
-- docs/10 [사고 기록]의 사고 1과 같은 상황이라 flyway repair 대신 번호를 옮겼다. repair를
-- 쓰면 남의 migration을 내 파일로 위장시켜 그쪽 스키마 변경이 기록에서 사라진다.

-- docs/19 4.11.3: 도착 좌표 검증과 "먼저 갈게요" 이탈.
--
-- arrival_distance_meters : 만남 장소 핀과 도착 시점 좌표 사이의 거리(미터)다. 체크인과 같이
--                           원본 좌표는 저장하지 않고 거리만 남긴다(개인정보처리방침 기준).
-- left_at                 : 본인이 "먼저 갈게요"로 나간 시각이다. 그룹이 종료되면서 정리된
--                           LEFT(자동)와 본인 의사로 나간 LEFT(수동)를 구분하려고 둔다.
--                           보상 판정에는 쓰지 않고 이력과 타임라인 표시에 쓴다.
ALTER TABLE match_group_members
    ADD COLUMN arrival_distance_meters INTEGER,
    ADD COLUMN left_at TIMESTAMPTZ;

ALTER TABLE match_group_members
    ADD CONSTRAINT chk_match_group_members_arrival_distance
        CHECK (arrival_distance_meters IS NULL OR arrival_distance_meters >= 0);

-- 타임라인에 "OO님이 먼저 갔어요"를 남긴다. MEMBER_CANCELLED(도착 전 취소)와 뜻이 다르다.
ALTER TABLE match_events
    DROP CONSTRAINT chk_match_events_type;

ALTER TABLE match_events
    ADD CONSTRAINT chk_match_events_type CHECK (event_type IN (
        'MATCH_PROPOSED',
        'MATCH_ACCEPTED',
        'MATCH_REJECTED',
        'MATCH_TIMEOUT',
        'MATCH_INSUFFICIENT_MEMBERS',
        'MATCH_CONFIRMED',
        'ARRIVAL_TIME_SELECTED',
        'MEMBER_ARRIVED',
        'MEMBER_CANCELLED',
        'MEMBER_NO_SHOW',
        'MEMBER_LEFT',
        'MATCH_COMPLETED',
        'MATCH_CANCELLED',
        'SAFETY_REMINDER'
    ));
