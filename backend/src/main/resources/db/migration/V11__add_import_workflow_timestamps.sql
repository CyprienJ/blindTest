ALTER TABLE track_import_jobs
    ADD COLUMN updated_at TIMESTAMP,
    ADD COLUMN completed_at TIMESTAMP;

UPDATE track_import_jobs
SET updated_at = created_at,
    completed_at = CASE
        WHEN status IN ('DONE', 'PUBLISHED', 'REJECTED', 'FAILED') THEN created_at
        ELSE NULL
    END;

ALTER TABLE track_import_jobs
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT NOW();
