CREATE TABLE tracks (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(255) NOT NULL,
    artist           VARCHAR(255) NOT NULL,
    composer         VARCHAR(255),
    work             VARCHAR(255),
    audio_url        VARCHAR(500) NOT NULL,
    image_url        VARCHAR(500),
    category         VARCHAR(100),
    decade           SMALLINT,
    duration_seconds INTEGER,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    youtube_url      VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tracks_status   ON tracks (status);
CREATE INDEX idx_tracks_category ON tracks (category);
CREATE INDEX idx_tracks_decade   ON tracks (decade);
