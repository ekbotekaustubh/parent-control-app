CREATE TABLE app_rules (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id            uuid NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    app_id              uuid NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    rule_type           text NOT NULL DEFAULT 'block' CHECK (rule_type IN ('block', 'allow')),
    daily_limit_minutes int,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (child_id, app_id)
);

CREATE INDEX idx_app_rules_child_id ON app_rules (child_id);
