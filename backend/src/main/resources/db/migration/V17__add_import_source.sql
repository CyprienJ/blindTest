ALTER TABLE track_import_jobs
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'YOUTUBE',
    ADD COLUMN original_filename VARCHAR(255);

ALTER TABLE track_import_jobs
    ALTER COLUMN youtube_url DROP NOT NULL;

ALTER TABLE track_import_jobs
    ADD CONSTRAINT chk_track_import_jobs_source
    CHECK (source IN ('YOUTUBE', 'UPLOAD'));

ALTER TABLE track_import_jobs
    ADD CONSTRAINT chk_track_import_jobs_source_reference
    CHECK (
        (source = 'YOUTUBE' AND youtube_url IS NOT NULL)
        OR (source = 'UPLOAD' AND youtube_url IS NULL)
    );
