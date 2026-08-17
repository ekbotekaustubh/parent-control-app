-- Optional per-app/per-day time window (docs/roadmap.md's "Schedules" section). When set,
-- this specific rule only takes effect while the referenced schedule is active; outside its
-- window the app reverts to unrestricted (no matching rule = allowed), same as any other
-- package with no app_rules row. ON DELETE SET NULL: deleting a schedule detaches the rule
-- rather than deleting it - the parent's block/allow choice for the app itself should survive.
ALTER TABLE app_rules ADD COLUMN schedule_id uuid NULL REFERENCES schedules(id) ON DELETE SET NULL;
