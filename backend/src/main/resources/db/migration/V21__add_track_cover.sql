-- Administrator-selected cover art, stored privately as covers/{cover_id}.jpg.
ALTER TABLE tracks
    ADD COLUMN cover_id UUID,
    ADD COLUMN cover_source VARCHAR(20),
    ADD CONSTRAINT uq_tracks_cover_id UNIQUE (cover_id),
    ADD CONSTRAINT chk_tracks_cover_source
        CHECK (cover_source IS NULL
            OR cover_source IN ('DEEZER', 'ITUNES', 'COVER_ART_ARCHIVE', 'UPLOAD')),
    ADD CONSTRAINT chk_tracks_cover_pair
        CHECK ((cover_id IS NULL) = (cover_source IS NULL));
