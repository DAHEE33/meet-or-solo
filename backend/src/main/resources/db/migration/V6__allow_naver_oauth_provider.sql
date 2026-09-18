ALTER TABLE members
    DROP CONSTRAINT chk_members_provider;

ALTER TABLE members
    ADD CONSTRAINT chk_members_provider CHECK (provider IN ('KAKAO', 'NAVER'));
