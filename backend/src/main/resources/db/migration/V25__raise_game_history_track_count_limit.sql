-- ADR-0024 raises the maximum game length from 30 to 500 tracks so a player can play a
-- whole playlist.
ALTER TABLE game_histories
    DROP CONSTRAINT chk_game_histories_track_count,
    ADD CONSTRAINT chk_game_histories_track_count CHECK (
        requested_track_count BETWEEN 5 AND 500
        AND total_rounds = requested_track_count
    );
