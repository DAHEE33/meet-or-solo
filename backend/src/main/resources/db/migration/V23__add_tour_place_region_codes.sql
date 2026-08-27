-- 관광지 목록의 지역(시군구) 필터를 위해 tour_places에 지역 코드 컬럼을 추가한다.
-- 기존 V1~V22는 수정하지 않는다.
--
-- festivals에는 area_code/sigungu_code가 처음부터 있었지만(V2), tour_places에는 없어서
-- 지역으로 조회할 수단이 좌표와 free-text address뿐이었다. 주소 텍스트 파싱은 시도 표기가
-- '강원특별자치도'와 '강원'으로 섞여 있어 신뢰할 수 없으므로 코드를 컬럼으로 승격한다.
-- 자세한 배경은 docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 2.2 참고.

ALTER TABLE tour_places ADD COLUMN area_code VARCHAR(20);
ALTER TABLE tour_places ADD COLUMN sigungu_code VARCHAR(20);

-- 기존 행 백필. raw_data에 TourAPI 원본의 lDongRegnCd(시도)/lDongSignguCd(시군구)가 전 건
-- 남아 있으므로 TourAPI를 다시 호출하지 않고 그대로 옮긴다. 최신 TourAPI는 구형 areacode/
-- sigungucode 대신 법정동 코드(lDong*)를 쓰며, 이 프로젝트도 조회 시 lDong* 파라미터를 보낸다.
UPDATE tour_places
SET area_code = NULLIF(TRIM(raw_data ->> 'lDongRegnCd'), ''),
    sigungu_code = NULLIF(TRIM(raw_data ->> 'lDongSignguCd'), '')
WHERE raw_data IS NOT NULL;

-- 지역 필터 조회용. 현재 4,000건 규모에서도 status+sigungu_code 조합 조회가 인덱스를 타도록 한다.
CREATE INDEX idx_tour_places_status_sigungu ON tour_places (status, sigungu_code);
