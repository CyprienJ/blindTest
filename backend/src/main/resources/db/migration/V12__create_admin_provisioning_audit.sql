CREATE TABLE admin_provisioning_audit (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    outcome         VARCHAR(30) NOT NULL,
    target_user_id  UUID,
    attempted_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_admin_provisioning_outcome
        CHECK (outcome IN (
            'PROMOTED',
            'ALREADY_ADMIN',
            'USER_NOT_FOUND',
            'EMAIL_NOT_VERIFIED',
            'ADMIN_ALREADY_EXISTS'
        ))
);

CREATE INDEX idx_admin_provisioning_audit_attempted_at
    ON admin_provisioning_audit (attempted_at);
