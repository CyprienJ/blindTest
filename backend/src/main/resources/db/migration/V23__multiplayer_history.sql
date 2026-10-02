-- MP-04: durable multiplayer history (ADR-0019).
-- Game and round totals now add up every participant (up to ten), and a skip or timeout
-- may keep points earned before it. Solo rows still satisfy the previous rules.

ALTER TABLE game_histories DROP CONSTRAINT chk_game_histories_totals;
ALTER TABLE game_histories ADD CONSTRAINT chk_game_histories_totals CHECK (
    total_score >= 0
    AND titles_found >= 0
    AND skipped_rounds >= 0
    AND timed_out_rounds >= 0
    AND titles_found + skipped_rounds + timed_out_rounds = total_rounds
    AND total_score <= total_rounds * 10000
);

ALTER TABLE game_history_rounds DROP CONSTRAINT chk_game_history_round_points;
ALTER TABLE game_history_rounds ADD CONSTRAINT chk_game_history_round_points CHECK (
    points BETWEEN 0 AND 10000
    AND (outcome <> 'TITLE_FOUND' OR points >= 100)
);

-- One row per participant and round, written only for multiplayer games; a solo round's
-- points column already holds its only participant's result.
CREATE TABLE game_history_round_results (
    id                    UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    game_history_round_id UUID     NOT NULL,
    participant_id        UUID     NOT NULL,
    title_found           BOOLEAN  NOT NULL,
    points                INTEGER  NOT NULL,
    CONSTRAINT fk_game_history_round_results_round
        FOREIGN KEY (game_history_round_id) REFERENCES game_history_rounds(id) ON DELETE CASCADE,
    CONSTRAINT uq_game_history_round_result UNIQUE (game_history_round_id, participant_id),
    CONSTRAINT chk_game_history_round_result_points CHECK (
        (title_found AND points BETWEEN 100 AND 1000)
        OR (NOT title_found AND points = 0)
    )
);
