-- Registration becomes invitation-only (ADR-0017). Accounts created through the former
-- public registration flow are removed; administrators are kept. Dependent rows follow the
-- existing foreign keys: refresh tokens cascade and game-history participants are anonymized.
DELETE FROM users WHERE role <> 'ADMIN';

CREATE TABLE invitations (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email            VARCHAR(255) NOT NULL,
    token_hash       VARCHAR(64)  NOT NULL UNIQUE,
    invited_by       UUID         REFERENCES users (id) ON DELETE SET NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMP    NOT NULL,
    accepted_at      TIMESTAMP,
    accepted_user_id UUID         REFERENCES users (id) ON DELETE SET NULL,
    revoked_at       TIMESTAMP,
    CONSTRAINT chk_invitations_email_lower CHECK (email = LOWER(email)),
    CONSTRAINT chk_invitations_single_outcome
        CHECK (accepted_at IS NULL OR revoked_at IS NULL)
);

-- At most one open invitation per address; replacing an invitation revokes the previous one.
CREATE UNIQUE INDEX uq_invitations_open_email
    ON invitations (email)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

CREATE INDEX idx_invitations_created_at ON invitations (created_at);
