CREATE TABLE pairing_sessions (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id              uuid NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    code_hash             text NOT NULL,
    status                text NOT NULL DEFAULT 'pending'
                              CHECK (status IN ('pending', 'claimed', 'expired', 'revoked')),
    expires_at            timestamptz NOT NULL,
    claimed_by_device_id  uuid REFERENCES devices(id),
    claimed_at            timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now()
);

-- Enforces "one active pairing code per child" at the database level.
CREATE UNIQUE INDEX idx_pairing_sessions_one_pending_per_child
    ON pairing_sessions (child_id) WHERE status = 'pending';

-- For the atomic claim lookup (WHERE code_hash = ... AND status = 'pending' ...).
CREATE INDEX idx_pairing_sessions_code_hash ON pairing_sessions (code_hash);

-- For expiry sweeps.
CREATE INDEX idx_pairing_sessions_status_expires_at ON pairing_sessions (status, expires_at);
