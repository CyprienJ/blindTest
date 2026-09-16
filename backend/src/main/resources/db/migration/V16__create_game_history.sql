CREATE TABLE game_histories (
    id                              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id                      UUID         NOT NULL,
    history_version                 VARCHAR(30)  NOT NULL,
    game_mode                       VARCHAR(20)  NOT NULL,
    protocol_version                SMALLINT     NOT NULL,
    configuration_version           VARCHAR(30)  NOT NULL,
    playlist_id_snapshot            UUID         NOT NULL,
    requested_track_count           INTEGER      NOT NULL,
    maximum_round_duration_seconds  INTEGER      NOT NULL,
    configuration_extensions        TEXT         NOT NULL DEFAULT '{}',
    total_score                     INTEGER      NOT NULL,
    total_rounds                    INTEGER      NOT NULL,
    titles_found                    INTEGER      NOT NULL,
    skipped_rounds                  INTEGER      NOT NULL,
    timed_out_rounds                INTEGER      NOT NULL,
    started_at                      TIMESTAMPTZ  NOT NULL,
    completed_at                    TIMESTAMPTZ  NOT NULL,
    retention_expires_at            TIMESTAMPTZ  NOT NULL,
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_game_histories_session UNIQUE (session_id),
    CONSTRAINT chk_game_histories_protocol_version CHECK (protocol_version > 0),
    CONSTRAINT chk_game_histories_track_count CHECK (
        requested_track_count BETWEEN 5 AND 30
        AND total_rounds = requested_track_count
    ),
    CONSTRAINT chk_game_histories_round_duration CHECK (
        maximum_round_duration_seconds BETWEEN 10 AND 60
        AND (maximum_round_duration_seconds - 10) % 5 = 0
    ),
    CONSTRAINT chk_game_histories_extensions_json CHECK (
        configuration_extensions IS JSON OBJECT
    ),
    CONSTRAINT chk_game_histories_totals CHECK (
        total_score >= 0
        AND titles_found >= 0
        AND skipped_rounds >= 0
        AND timed_out_rounds >= 0
        AND titles_found + skipped_rounds + timed_out_rounds = total_rounds
        AND total_score <= total_rounds * 1000
    ),
    CONSTRAINT chk_game_histories_timing CHECK (started_at <= completed_at),
    CONSTRAINT chk_game_histories_retention CHECK (
        retention_expires_at > completed_at
        AND retention_expires_at <= completed_at + INTERVAL '24 months'
    )
);

CREATE INDEX idx_game_histories_completed_at
    ON game_histories (completed_at DESC);
CREATE INDEX idx_game_histories_retention_expires_at
    ON game_histories (retention_expires_at);

CREATE TABLE game_history_participants (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    game_history_id     UUID         NOT NULL,
    participant_id      UUID         NOT NULL,
    user_id             UUID,
    display_name        VARCHAR(100) NOT NULL,
    participant_kind    VARCHAR(20)  NOT NULL,
    owner               BOOLEAN      NOT NULL DEFAULT FALSE,
    final_score         INTEGER      NOT NULL,
    final_rank          INTEGER      NOT NULL,
    CONSTRAINT fk_game_history_participants_history
        FOREIGN KEY (game_history_id) REFERENCES game_histories(id) ON DELETE CASCADE,
    CONSTRAINT fk_game_history_participants_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_game_history_participant
        UNIQUE (game_history_id, participant_id),
    CONSTRAINT uq_game_history_participant_user
        UNIQUE (game_history_id, user_id),
    CONSTRAINT chk_game_history_participant_kind CHECK (
        participant_kind IN ('AUTHENTICATED', 'GUEST')
    ),
    CONSTRAINT chk_game_history_participant_score CHECK (final_score >= 0),
    CONSTRAINT chk_game_history_participant_rank CHECK (final_rank > 0)
);

CREATE UNIQUE INDEX uq_game_history_owner
    ON game_history_participants (game_history_id)
    WHERE owner;
CREATE INDEX idx_game_history_participants_user
    ON game_history_participants (user_id)
    WHERE user_id IS NOT NULL;

CREATE TABLE game_history_rounds (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    game_history_id          UUID         NOT NULL,
    round_index              INTEGER      NOT NULL,
    track_id_snapshot        UUID,
    title_snapshot           VARCHAR(255) NOT NULL,
    artist_snapshot          VARCHAR(255) NOT NULL,
    composer_snapshot        VARCHAR(255),
    album_snapshot           VARCHAR(255),
    answer_aliases_snapshot  TEXT         NOT NULL DEFAULT '{}',
    answer_snapshot_version  VARCHAR(30)  NOT NULL,
    excerpt_start_seconds    INTEGER      NOT NULL,
    outcome                  VARCHAR(20)  NOT NULL,
    points                   INTEGER      NOT NULL,
    scoring_rule_version     VARCHAR(30)  NOT NULL,
    started_at               TIMESTAMPTZ  NOT NULL,
    deadline                 TIMESTAMPTZ  NOT NULL,
    revealed_at              TIMESTAMPTZ  NOT NULL,
    completed_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_game_history_rounds_history
        FOREIGN KEY (game_history_id) REFERENCES game_histories(id) ON DELETE CASCADE,
    CONSTRAINT uq_game_history_round_order UNIQUE (game_history_id, round_index),
    CONSTRAINT chk_game_history_round_index CHECK (round_index >= 0),
    CONSTRAINT chk_game_history_round_aliases_json CHECK (
        answer_aliases_snapshot IS JSON OBJECT
    ),
    CONSTRAINT chk_game_history_round_excerpt CHECK (excerpt_start_seconds >= 0),
    CONSTRAINT chk_game_history_round_outcome CHECK (
        outcome IN ('TITLE_FOUND', 'SKIPPED', 'TIMEOUT')
    ),
    CONSTRAINT chk_game_history_round_points CHECK (
        (outcome = 'TITLE_FOUND' AND points BETWEEN 100 AND 1000)
        OR (outcome IN ('SKIPPED', 'TIMEOUT') AND points = 0)
    ),
    CONSTRAINT chk_game_history_round_timing CHECK (
        started_at < deadline
        AND started_at <= revealed_at
        AND revealed_at <= completed_at
    )
);

CREATE INDEX idx_game_history_rounds_track_snapshot
    ON game_history_rounds (track_id_snapshot)
    WHERE track_id_snapshot IS NOT NULL;
