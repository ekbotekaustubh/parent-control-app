CREATE TABLE schedules (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id       uuid NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    name           text NOT NULL,
    -- Comma-separated 3-letter day codes, e.g. 'MON,TUE,WED,THU,FRI'. Plain text rather than
    -- a bitmask/array column to stay consistent with this codebase's existing preference for
    -- human-debuggable text over packed encodings (see rule_type, status columns elsewhere).
    days_of_week   text NOT NULL,
    start_time     time NOT NULL,
    end_time       time NOT NULL,
    -- 'block': every app is blocked while active (bedtime/lockdown).
    -- 'allow_only': every app is blocked while active EXCEPT ones with a standing ALLOW
    -- app_rules row (school-hours/focus mode). See EnforcementDecider's KDoc (child-app).
    mode           text NOT NULL DEFAULT 'block' CHECK (mode IN ('block', 'allow_only')),
    is_active      boolean NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_schedules_child_id ON schedules (child_id);
