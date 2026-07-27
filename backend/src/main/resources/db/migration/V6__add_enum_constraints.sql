ALTER TABLE users
    ADD CONSTRAINT chk_users_role
    CHECK (role IN ('USER', 'ADMIN'));

ALTER TABLE tracks
    ADD CONSTRAINT chk_tracks_status
    CHECK (status IN ('PENDING', 'PUBLISHED', 'REJECTED'));

ALTER TABLE track_import_jobs
    ADD CONSTRAINT chk_track_import_jobs_status
    CHECK (status IN (
        'QUEUED',
        'DOWNLOADING',
        'PROCESSING',
        'DONE',
        'PUBLISHED',
        'REJECTED',
        'FAILED'
    ));
