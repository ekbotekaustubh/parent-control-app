CREATE TABLE category_rules (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id            uuid NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    category            text NOT NULL,
    daily_limit_minutes int NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (child_id, category)
);

CREATE INDEX idx_category_rules_child_id ON category_rules (child_id);
