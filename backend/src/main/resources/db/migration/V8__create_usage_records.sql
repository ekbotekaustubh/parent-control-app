CREATE TABLE usage_records (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id         uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    child_id          uuid NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    app_id            uuid NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    usage_date        date NOT NULL,
    duration_minutes  int NOT NULL DEFAULT 0,
    last_updated_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (device_id, app_id, usage_date)
);

CREATE INDEX idx_usage_records_child_id_usage_date ON usage_records (child_id, usage_date);
