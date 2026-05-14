CREATE TABLE playlists (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    image_url   VARCHAR(500),
    created_by  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE playlist_tracks (
    playlist_id UUID    NOT NULL REFERENCES playlists (id) ON DELETE CASCADE,
    track_id    UUID    NOT NULL REFERENCES tracks (id)    ON DELETE CASCADE,
    "order"     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (playlist_id, track_id)
);
