ALTER TABLE track_import_jobs
    ADD COLUMN youtube_video_id VARCHAR(11);

CREATE UNIQUE INDEX uq_track_import_jobs_active_video
    ON track_import_jobs (youtube_video_id)
    WHERE youtube_video_id IS NOT NULL
      AND status IN ('QUEUED', 'DOWNLOADING', 'PROCESSING');
