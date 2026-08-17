CREATE TABLE devices (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id                  uuid NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    device_name               text,
    model                     text,
    os_version                text,
    app_version               text,
    status                    text NOT NULL DEFAULT 'pending_approval'
                                  CHECK (status IN ('pending_approval', 'approved', 'revoked')),
    pending_claim_ticket_hash text,
    claim_ticket_expires_at   timestamptz,
    last_seen_at              timestamptz,
    last_sync_at              timestamptz,
    paired_at                 timestamptz,
    approved_at               timestamptz,
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_devices_child_id ON devices (child_id);
CREATE INDEX idx_devices_status ON devices (status);
