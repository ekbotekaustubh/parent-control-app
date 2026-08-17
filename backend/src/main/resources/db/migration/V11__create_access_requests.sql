CREATE TABLE access_requests (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id           uuid NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    app_id             uuid NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    requested_minutes  int NOT NULL,
    status             text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'denied')),
    resolved_minutes   int,
    created_at         timestamptz NOT NULL DEFAULT now(),
    resolved_at        timestamptz,
    updated_at         timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_access_requests_child_id_status ON access_requests (child_id, status);
