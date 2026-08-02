DELETE FROM playlist_tracks AS playlist_track
USING tracks AS track
WHERE playlist_track.track_id = track.id
  AND track.status <> 'PUBLISHED';

WITH ranked AS (
    SELECT playlist_id,
           track_id,
           ROW_NUMBER() OVER (
               PARTITION BY playlist_id
               ORDER BY "order", track_id
           ) - 1 AS normalized_order
    FROM playlist_tracks
)
UPDATE playlist_tracks AS playlist_track
SET "order" = ranked.normalized_order
FROM ranked
WHERE playlist_track.playlist_id = ranked.playlist_id
  AND playlist_track.track_id = ranked.track_id;

ALTER TABLE playlist_tracks
    ADD CONSTRAINT chk_playlist_tracks_order_non_negative CHECK ("order" >= 0),
    ADD CONSTRAINT uq_playlist_tracks_order
        UNIQUE (playlist_id, "order") DEFERRABLE INITIALLY DEFERRED;
