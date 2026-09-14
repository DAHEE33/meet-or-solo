-- 축제·관광지 목록이 좋아요(찜) 수와 후기(댓글) 수를 함께 집계하고 그 값으로 정렬하면서
-- 필요해진 인덱스다(docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md).
--
-- V26이 만든 uq_content_bookmarks_member_festival / uq_content_bookmarks_member_place는
-- member_id가 선행 컬럼이라 "이 축제를 찜한 사람 수" 같은 대상별 집계에 쓰이지 않는다.
-- 관광지가 4,000건대라 대상별 인덱스가 없으면 목록 한 페이지마다 전체 스캔이 반복된다.
--
-- 댓글은 V26의 idx_content_comments_festival_visible / idx_content_comments_place_visible이
-- 이미 (대상, id DESC) WHERE status = 'VISIBLE' partial index라 집계에 그대로 쓰인다.
-- 그래서 댓글 쪽에는 인덱스를 추가하지 않는다.
CREATE INDEX idx_content_bookmarks_festival
    ON content_bookmarks (festival_id) WHERE festival_id IS NOT NULL;
CREATE INDEX idx_content_bookmarks_place
    ON content_bookmarks (tour_place_id) WHERE tour_place_id IS NOT NULL;
