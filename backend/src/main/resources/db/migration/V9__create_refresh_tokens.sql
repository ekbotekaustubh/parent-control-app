CREATE TABLE parent_refresh_tokens (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id       uuid NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    token_hash      text NOT NULL,
    issued_at       timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL,
    revoked_at      timestamptz,
    replaced_by_id  uuid REFERENCES parent_refresh_tokens(id),
    user_agent      text,
    ip_address      text
);

CREATE INDEX idx_parent_refresh_tokens_parent_id ON parent_refresh_tokens (parent_id);
CREATE INDEX idx_parent_refresh_tokens_token_hash ON parent_refresh_tokens (token_hash);

CREATE TABLE device_refresh_tokens (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    token_hash      text NOT NULL,
    issued_at       timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL,
    revoked_at      timestamptz,
    replaced_by_id  uuid REFERENCES device_refresh_tokens(id),
    user_agent      text,
    ip_address      text
);

CREATE INDEX idx_device_refresh_tokens_device_id ON device_refresh_tokens (device_id);
CREATE INDEX idx_device_refresh_tokens_token_hash ON device_refresh_tokens (token_hash);
