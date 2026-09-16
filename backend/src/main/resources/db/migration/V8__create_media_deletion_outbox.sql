CREATE TABLE media_deletion_outbox (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    object_key      VARCHAR(1024) NOT NULL UNIQUE,
    attempt_count   INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    last_error      VARCHAR(255),
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_media_deletion_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_media_deletion_outbox_due
    ON media_deletion_outbox (next_attempt_at);
