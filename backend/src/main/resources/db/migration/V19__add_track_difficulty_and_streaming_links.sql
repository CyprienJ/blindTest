ALTER TABLE tracks
    ADD COLUMN difficulty SMALLINT,
    ADD COLUMN deezer_url VARCHAR(500),
    ADD COLUMN streaming_youtube_url VARCHAR(500),
    ADD COLUMN spotify_url VARCHAR(500),
    ADD COLUMN qobuz_url VARCHAR(500),
    ADD COLUMN tidal_url VARCHAR(500),
    ADD COLUMN soundcloud_url VARCHAR(500),
    ADD CONSTRAINT chk_tracks_difficulty
        CHECK (difficulty IS NULL OR difficulty BETWEEN 1 AND 5);
