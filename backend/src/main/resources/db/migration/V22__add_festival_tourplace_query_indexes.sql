-- 성능 개선: festivals/tour_places 목록·검색·반경 조회 쿼리를 위한 인덱스를 추가한다.
-- 기존 V1~V21은 수정하지 않는다.

-- LIKE '%keyword%' 검색이 순차 스캔 대신 인덱스를 타도록 trigram 확장을 사용한다.
-- pgvector/pgvector:pg16 이미지는 공식 postgres:16 image를 기반으로 하므로 contrib 확장인
-- pg_trgm을 그대로 사용할 수 있다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- festivals: 목록/검색 조회(status + event_end_date 조합)를 위한 복합 인덱스.
-- 기존 idx_festivals_status, idx_festivals_period(개별 컬럼)는 다른 조회(만료 처리 batch 등)에서
-- 계속 쓰이므로 삭제하지 않는다.
CREATE INDEX idx_festivals_status_event_end_date ON festivals (status, event_end_date);

-- festivals: 제목 키워드 검색을 위한 GIN trigram 인덱스.
CREATE INDEX idx_festivals_title_trgm ON festivals USING GIN (lower(title) gin_trgm_ops);

-- festivals: 반경 검색(bounding box 사전 필터)을 위한 좌표 인덱스.
CREATE INDEX idx_festivals_status_coordinates ON festivals (status, map_x, map_y);

-- tour_places: 제목 키워드 검색을 위한 GIN trigram 인덱스.
CREATE INDEX idx_tour_places_title_trgm ON tour_places USING GIN (lower(title) gin_trgm_ops);

-- tour_places: 반경 검색(bounding box 사전 필터)을 위한 좌표 인덱스.
CREATE INDEX idx_tour_places_status_coordinates ON tour_places (status, map_x, map_y);
