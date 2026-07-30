ALTER TABLE match_group_members
    DROP CONSTRAINT chk_match_group_members_arrival_minutes;

ALTER TABLE match_group_members
    ADD CONSTRAINT chk_match_group_members_arrival_minutes
    CHECK (arrival_minutes IS NULL OR arrival_minutes IN (0, 5, 10, 20, 25, 30));
