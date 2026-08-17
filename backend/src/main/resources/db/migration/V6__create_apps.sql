-- Global catalog, not per-child.
CREATE TABLE apps (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    package_name text NOT NULL UNIQUE,
    display_name text,
    category     text,
    icon_url     text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);
