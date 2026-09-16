ALTER TABLE tracks
    ADD COLUMN excerpt_start_seconds INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_tracks_excerpt_start_seconds
        CHECK (excerpt_start_seconds >= 0);

CREATE TABLE track_answer_aliases (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    track_id         UUID         NOT NULL,
    answer_type      VARCHAR(20)  NOT NULL,
    alias_value      VARCHAR(255) NOT NULL,
    normalized_value VARCHAR(512) NOT NULL,
    CONSTRAINT fk_track_answer_aliases_track
        FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE,
    CONSTRAINT chk_track_answer_aliases_type
        CHECK (answer_type IN ('TITLE', 'ARTIST', 'COMPOSER', 'ALBUM')),
    CONSTRAINT chk_track_answer_aliases_value
        CHECK (BTRIM(alias_value) <> ''),
    CONSTRAINT chk_track_answer_aliases_normalized_value
        CHECK (BTRIM(normalized_value) <> ''),
    CONSTRAINT uq_track_answer_aliases_normalized
        UNIQUE (track_id, answer_type, normalized_value)
);

CREATE INDEX idx_track_answer_aliases_track
    ON track_answer_aliases (track_id);
