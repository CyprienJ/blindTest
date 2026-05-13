CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TYPE user_role AS ENUM ('USER', 'ADMIN');
CREATE TYPE track_status AS ENUM ('PENDING', 'PUBLISHED', 'REJECTED');
CREATE TYPE import_status AS ENUM ('PENDING', 'PROCESSING', 'DONE', 'FAILED');
CREATE TYPE friend_status AS ENUM ('PENDING', 'ACCEPTED', 'DECLINED');

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(500),
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    role            user_role    NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_connexion  TIMESTAMP
);

CREATE TABLE tracks (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(255) NOT NULL,
    artist           VARCHAR(255) NOT NULL,
    composer         VARCHAR(255),
    work             VARCHAR(255),
    audio_url        VARCHAR(500) NOT NULL,
    image_url        VARCHAR(500),
    category         VARCHAR(100),
    decade           SMALLINT,
    duration_seconds INTEGER,
    status           track_status NOT NULL DEFAULT 'PENDING',
    youtube_url      VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

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

CREATE TABLE track_import_jobs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    youtube_url   VARCHAR(500)  NOT NULL,
    status        import_status NOT NULL DEFAULT 'PENDING',
    progress      SMALLINT      NOT NULL DEFAULT 0,
    error_message TEXT,
    admin_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    track_id      UUID          REFERENCES tracks (id) ON DELETE SET NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE friend_requests (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_user_id UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    to_user_id   UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       friend_status NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMP,
    CONSTRAINT no_self_friend CHECK (from_user_id <> to_user_id),
    UNIQUE (from_user_id, to_user_id)
);

CREATE TABLE game_history (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_code VARCHAR(10) NOT NULL,
    played_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    track_count  INTEGER     NOT NULL DEFAULT 0,
    player_count INTEGER     NOT NULL DEFAULT 0
);

CREATE TABLE game_history_players (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id     UUID    NOT NULL REFERENCES game_history (id) ON DELETE CASCADE,
    user_id     UUID    REFERENCES users (id) ON DELETE SET NULL,
    guest_name  VARCHAR(50),
    final_score INTEGER NOT NULL DEFAULT 0,
    rank        INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT user_or_guest CHECK (user_id IS NOT NULL OR guest_name IS NOT NULL)
);

CREATE INDEX idx_tracks_status       ON tracks (status);
CREATE INDEX idx_tracks_category     ON tracks (category);
CREATE INDEX idx_tracks_decade       ON tracks (decade);
CREATE INDEX idx_friend_requests_to  ON friend_requests (to_user_id);
CREATE INDEX idx_game_history_code   ON game_history (session_code);
CREATE INDEX idx_ghp_game            ON game_history_players (game_id);
CREATE INDEX idx_ghp_user            ON game_history_players (user_id);
CREATE INDEX idx_import_jobs_admin   ON track_import_jobs (admin_id);
