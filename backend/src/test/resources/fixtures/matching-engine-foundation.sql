-- 격리된 PostgreSQL 통합 테스트 DB 전용 fixture입니다.
-- V1~V11 적용 후, 테스트 transaction 안에서 실행하고 rollback하는 것을 전제로 합니다.
-- 운영 profile, 운영 jar 초기화, Flyway migration에서는 이 파일을 실행하지 않습니다.

INSERT INTO festivals (
    id, content_id, content_type_id, title, area_code, event_start_date, event_end_date,
    map_x, map_y, status, created_at, updated_at
) VALUES
    (9100001, 'fixture-festival-1', '15', '매칭 테스트 강원 축제', '32', '2026-07-01', '2026-07-31',
     128.1000000000, 37.1000000000, 'ACTIVE', '2026-07-17T14:00:00+09:00', '2026-07-17T14:00:00+09:00'),
    (9100002, 'fixture-festival-2', '15', '다른 강원 축제', '32', '2026-07-01', '2026-07-31',
     128.2000000000, 37.2000000000, 'ACTIVE', '2026-07-17T14:00:00+09:00', '2026-07-17T14:00:00+09:00');

INSERT INTO members (
    id, provider, provider_user_id, nickname, role, status, created_at, updated_at
) SELECT
    member_id, 'KAKAO', 'matching-fixture-' || member_id, 'fixture' || member_id, 'USER', 'ACTIVE',
    '2026-07-17T14:00:00+09:00', '2026-07-17T14:00:00+09:00'
FROM generate_series(9110001, 9110011) AS series(member_id);

INSERT INTO member_travel_styles (member_id, style_code, created_at)
SELECT member_id, 'PHOTO', '2026-07-17T14:00:00+09:00'
FROM generate_series(9110001, 9110011) AS series(member_id);

INSERT INTO festival_checkins (
    id, member_id, festival_id, distance_meters, status, checked_in_at, expires_at, created_at, updated_at
) SELECT
    9120000 + member_id - 9110000,
    member_id,
    CASE WHEN member_id = 9110004 THEN 9100002 ELSE 9100001 END,
    100,
    CASE WHEN member_id = 9110009 THEN 'EXPIRED' ELSE 'ACTIVE' END,
    '2026-07-17T14:50:00+09:00',
    CASE WHEN member_id = 9110008
         THEN '2026-07-17T14:59:59+09:00'::timestamptz
         ELSE '2026-07-17T16:00:00+09:00'::timestamptz END,
    '2026-07-17T14:50:00+09:00',
    '2026-07-17T14:50:00+09:00'
FROM generate_series(9110001, 9110011) AS series(member_id);

INSERT INTO match_pools (
    id, member_id, festival_id, checkin_id, preferred_group_size, allow_minimum_two,
    tags, status, entered_at, search_expires_at, created_at, updated_at
) VALUES
    (9120001, 9110001, 9100001, 9120001, 4, TRUE, '["PHOTO"]', 'WAITING', '2026-07-17T14:59:50+09:00', '2026-07-17T15:01:00+09:00', '2026-07-17T14:59:50+09:00', '2026-07-17T14:59:50+09:00'),
    (9120002, 9110002, 9100001, 9120002, 4, TRUE, '["PHOTO"]', 'WAITING', '2026-07-17T14:59:50+09:00', '2026-07-17T15:01:00+09:00', '2026-07-17T14:59:50+09:00', '2026-07-17T14:59:50+09:00'),
    (9120003, 9110003, 9100001, 9120003, 4, TRUE, '["PHOTO"]', 'WAITING', '2026-07-17T14:58:50+09:00', '2026-07-17T14:59:59+09:00', '2026-07-17T14:58:50+09:00', '2026-07-17T14:58:50+09:00'),
    (9120004, 9110004, 9100002, 9120004, 4, TRUE, '["PHOTO"]', 'WAITING', '2026-07-17T14:59:50+09:00', '2026-07-17T15:01:00+09:00', '2026-07-17T14:59:50+09:00', '2026-07-17T14:59:50+09:00'),
    (9120005, 9110005, 9100001, 9120005, 4, TRUE, '["PHOTO"]', 'PROPOSED', '2026-07-17T14:59:50+09:00', '2026-07-17T15:01:00+09:00', '2026-07-17T14:59:50+09:00', '2026-07-17T14:59:50+09:00'),
    (9120006, 9110006, 9100001, 9120006, 4, TRUE, '["PHOTO"]', 'WAITING', '2026-07-17T14:59:50+09:00', '2026-07-17T15:01:00+09:00', '2026-07-17T14:59:50+09:00', '2026-07-17T14:59:50+09:00'),
    (9120007, 9110007, 9100001, 9120007, 4, TRUE, '["PHOTO"]', 'WAITING', '2026-07-17T14:59:50+09:00', '2026-07-17T15:01:00+09:00', '2026-07-17T14:59:50+09:00', '2026-07-17T14:59:50+09:00'),
    (9120008, 9110008, 9100001, 9120008, 4, TRUE, '["PHOTO"]', 'WAITING', '2026-07-17T14:59:50+09:00', '2026-07-17T15:01:00+09:00', '2026-07-17T14:59:50+09:00', '2026-07-17T14:59:50+09:00'),
    (9120009, 9110009, 9100001, 9120009, 4, TRUE, '["PHOTO"]', 'WAITING', '2026-07-17T14:59:50+09:00', '2026-07-17T15:01:00+09:00', '2026-07-17T14:59:50+09:00', '2026-07-17T14:59:50+09:00'),
    (9120010, 9110010, 9100001, 9120010, 4, TRUE, '["PHOTO"]', 'WAITING', '2026-07-17T14:59:50+09:00', '2026-07-17T15:01:00+09:00', '2026-07-17T14:59:50+09:00', '2026-07-17T14:59:50+09:00'),
    (9120011, 9110011, 9100001, 9120011, 4, TRUE, '["PHOTO"]', 'WAITING', '2026-07-17T14:59:40+09:00', '2026-07-17T15:01:00+09:00', '2026-07-17T14:59:40+09:00', '2026-07-17T14:59:40+09:00');

INSERT INTO user_blocks (id, blocker_member_id, blocked_member_id, reason, created_at)
VALUES
    (9150001, 9110001, 9110006, 'TEST_FIXTURE', '2026-07-17T14:55:00+09:00'),
    (9150002, 9110010, 9110001, 'TEST_FIXTURE', '2026-07-17T14:55:00+09:00');

INSERT INTO match_cooldowns (id, member_id, reason, status, starts_at, expires_at, created_at)
VALUES (9160001, 9110007, 'TIMEOUT', 'ACTIVE', '2026-07-17T14:59:00+09:00', '2026-07-17T15:04:00+09:00', '2026-07-17T14:59:00+09:00');

INSERT INTO match_attempts (
    id, festival_id, target_group_size, status, score, created_by, started_at, expires_at, created_at, updated_at
) VALUES (
    9130001, 9100001, 4, 'INSUFFICIENT_MEMBERS', 80.00, 'SCHEDULER',
    '2026-07-17T14:59:30+09:00', '2026-07-17T15:02:00+09:00',
    '2026-07-17T14:59:30+09:00', '2026-07-17T14:59:50+09:00'
);

INSERT INTO match_proposals (
    id, attempt_id, member_id, proposal_type, proposal_round, status,
    sent_at, expires_at, responded_at, created_at, updated_at
) VALUES
    (9140001, 9130001, 9110001, 'INITIAL_MATCH', 1, 'ACCEPTED', '2026-07-17T14:59:30+09:00', '2026-07-17T15:00:00+09:00', '2026-07-17T14:59:40+09:00', '2026-07-17T14:59:30+09:00', '2026-07-17T14:59:40+09:00'),
    (9140002, 9130001, 9110002, 'INITIAL_MATCH', 1, 'ACCEPTED', '2026-07-17T14:59:30+09:00', '2026-07-17T15:00:00+09:00', '2026-07-17T14:59:41+09:00', '2026-07-17T14:59:30+09:00', '2026-07-17T14:59:41+09:00'),
    (9140003, 9130001, 9110003, 'INITIAL_MATCH', 1, 'REJECTED', '2026-07-17T14:59:30+09:00', '2026-07-17T15:00:00+09:00', '2026-07-17T14:59:42+09:00', '2026-07-17T14:59:30+09:00', '2026-07-17T14:59:42+09:00'),
    (9140004, 9130001, 9110004, 'INITIAL_MATCH', 1, 'TIMEOUT', '2026-07-17T14:59:30+09:00', '2026-07-17T15:00:00+09:00', '2026-07-17T15:00:00+09:00', '2026-07-17T14:59:30+09:00', '2026-07-17T15:00:00+09:00'),
    (9140005, 9130001, 9110001, 'INSUFFICIENT_MEMBERS_CONFIRMATION', 2, 'SENT', '2026-07-17T15:00:01+09:00', '2026-07-17T15:00:31+09:00', NULL, '2026-07-17T15:00:01+09:00', '2026-07-17T15:00:01+09:00'),
    (9140006, 9130001, 9110002, 'INSUFFICIENT_MEMBERS_CONFIRMATION', 2, 'SENT', '2026-07-17T15:00:01+09:00', '2026-07-17T15:00:31+09:00', NULL, '2026-07-17T15:00:01+09:00', '2026-07-17T15:00:01+09:00');
