-- Administrator-uploaded playlist cover, stored privately as covers/{cover_id}.jpg like
-- track covers. A stored cover takes precedence over the legacy external image_url.
ALTER TABLE playlists
    ADD COLUMN cover_id UUID,
    ADD CONSTRAINT uq_playlists_cover_id UNIQUE (cover_id);
