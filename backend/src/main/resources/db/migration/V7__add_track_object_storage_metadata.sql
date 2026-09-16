ALTER TABLE tracks
    ALTER COLUMN audio_url DROP NOT NULL,
    ADD COLUMN audio_object_key VARCHAR(1024),
    ADD COLUMN audio_checksum VARCHAR(64),
    ADD COLUMN audio_size_bytes BIGINT;

