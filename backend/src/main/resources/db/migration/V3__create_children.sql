CREATE TABLE children (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id      uuid NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    name           text NOT NULL,
    birth_year     int,
    config_version bigint NOT NULL DEFAULT 1,
    status         text NOT NULL DEFAULT 'active',
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_children_parent_id ON children (parent_id);
