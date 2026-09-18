ALTER TABLE members
    ADD COLUMN email VARCHAR(255);

DELETE FROM refresh_tokens older
USING refresh_tokens newer
WHERE older.member_id = newer.member_id
  AND (
      older.created_at < newer.created_at
      OR (older.created_at = newer.created_at AND older.id < newer.id)
  );

ALTER TABLE refresh_tokens
    ADD CONSTRAINT uq_refresh_tokens_member UNIQUE (member_id);
