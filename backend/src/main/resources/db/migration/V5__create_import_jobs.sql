CREATE TABLE track_import_jobs (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    youtube_url   VARCHAR(500) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    progress      SMALLINT     NOT NULL DEFAULT 0,
    error_message TEXT,
    admin_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    track_id      UUID         REFERENCES tracks (id) ON DELETE SET NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_import_jobs_admin  ON track_import_jobs (admin_id);
CREATE INDEX idx_import_jobs_status ON track_import_jobs (status);
