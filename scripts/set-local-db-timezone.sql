-- 기존 TIMESTAMPTZ 값은 수정하지 않고 신규 연결의 표시 timezone만 변경한다.
ALTER DATABASE meet_or_solo_local SET timezone TO 'Asia/Seoul';

-- ALTER DATABASE 설정은 신규 연결부터 적용되므로 실행 후 반드시 재접속한다.
SHOW TIME ZONE;
SELECT NOW();
