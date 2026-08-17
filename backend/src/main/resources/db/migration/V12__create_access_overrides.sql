CREATE TABLE access_overrides (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id           uuid NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    app_id             uuid NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    access_request_id  uuid NOT NULL REFERENCES access_requests(id) ON DELETE CASCADE,
    extra_minutes      int NOT NULL,
    expires_at         timestamptz NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (access_request_id)
);

CREATE INDEX idx_access_overrides_child_id_expires_at ON access_overrides (child_id, expires_at);
