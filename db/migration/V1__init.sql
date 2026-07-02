CREATE TABLE schema_history_placeholder (
    id BIGSERIAL PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schema_history_placeholder (description)
VALUES ('initial schema placeholder');
