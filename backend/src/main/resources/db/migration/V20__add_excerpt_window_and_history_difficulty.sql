ALTER TABLE tracks
    ADD COLUMN excerpt_duration_seconds INTEGER NOT NULL DEFAULT 60,
    ADD CONSTRAINT chk_tracks_excerpt_duration_seconds
        CHECK (excerpt_duration_seconds BETWEEN 5 AND 60);

-- Games completed before difficulty filtering existed included every level.
ALTER TABLE game_histories
    ADD COLUMN difficulty_levels VARCHAR(9) NOT NULL DEFAULT '1,2,3,4,5',
    ADD COLUMN include_unrated BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT chk_game_histories_difficulty_levels
        CHECK (difficulty_levels ~ '^([1-5](,[1-5]){0,4})?$'),
    ADD CONSTRAINT chk_game_histories_difficulty_selection
        CHECK (difficulty_levels <> '' OR include_unrated);

ALTER TABLE game_history_rounds
    ADD COLUMN difficulty_snapshot SMALLINT,
    ADD COLUMN excerpt_duration_seconds INTEGER NOT NULL DEFAULT 60,
    ADD CONSTRAINT chk_game_history_round_difficulty
        CHECK (difficulty_snapshot IS NULL OR difficulty_snapshot BETWEEN 1 AND 5),
    ADD CONSTRAINT chk_game_history_round_excerpt_duration
        CHECK (excerpt_duration_seconds BETWEEN 1 AND 60);
