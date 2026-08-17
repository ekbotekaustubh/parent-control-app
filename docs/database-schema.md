# Database Schema

PostgreSQL 16. Schema is defined by Flyway migrations in
`backend/src/main/resources/db/migration/` — this document describes the same schema in
prose; the SQL files are the source of truth.

Exposed table objects in `backend/.../db/tables/` are hand-written to mirror these
migrations (Exposed's auto-DDL is intentionally not used, to avoid the schema drifting
from the migration history as more tables are added later).

## Tables (built in this slice)

### `parents`
| column | type | notes |
|---|---|---|
| id | uuid PK | `gen_random_uuid()` |
| email | text UNIQUE NOT NULL | |
| password_hash | text NOT NULL | BCrypt |
| display_name | text | |
| timezone | text DEFAULT 'UTC' | unused this slice; needed later for local-day boundaries in schedules/reports |
| status | text DEFAULT 'active' | |
| created_at / updated_at | timestamptz | |

### `children`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| parent_id | uuid FK → parents(id) ON DELETE CASCADE | indexed |
| name | text NOT NULL | |
| birth_year | int NULL | age-band only, not full DOB — data minimization |
| config_version | bigint NOT NULL DEFAULT 1 | bumped whenever this child's `app_rules` change; lets `/device/config` and the dashboard cheaply detect "nothing changed" |
| status | text DEFAULT 'active' | |
| created_at / updated_at | timestamptz | |

### `devices`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| child_id | uuid FK → children(id) ON DELETE CASCADE | indexed |
| device_name, model, os_version, app_version | text | |
| status | text NOT NULL DEFAULT 'pending_approval' CHECK IN ('pending_approval','approved','revoked') | indexed |
| pending_claim_ticket_hash | text NULL | SHA-256 of the claim ticket |
| claim_ticket_expires_at | timestamptz NULL | |
| last_seen_at | timestamptz NULL | updated on any device API call |
| last_sync_at | timestamptz NULL | updated on `/device/usage-sync` |
| paired_at, approved_at | timestamptz NULL | |
| created_at / updated_at | timestamptz | |

### `pairing_sessions`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| child_id | uuid FK → children(id) ON DELETE CASCADE | |
| code_hash | text NOT NULL | SHA-256 of the plaintext code; plaintext is never stored |
| status | text NOT NULL DEFAULT 'pending' CHECK IN ('pending','claimed','expired','revoked') | |
| expires_at | timestamptz NOT NULL | 10 minutes from creation |
| claimed_by_device_id | uuid NULL FK → devices(id) | |
| claimed_at, created_at | timestamptz | |

Partial unique index: `CREATE UNIQUE INDEX ON pairing_sessions (child_id) WHERE status = 'pending';`
— enforces "one active pairing code per child" at the database level.
Index on `(status, expires_at)` for expiry sweeps.

### `apps`
Global catalog, not per-child.
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| package_name | text UNIQUE NOT NULL | e.g. `com.google.android.youtube` |
| display_name | text NULL | |
| category | text NULL | unused this slice — stub for future category rules |
| icon_url | text NULL | |
| created_at / updated_at | timestamptz | |

### `app_rules`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| child_id | uuid FK → children(id) ON DELETE CASCADE | |
| app_id | uuid FK → apps(id) ON DELETE CASCADE | |
| rule_type | text NOT NULL DEFAULT 'block' CHECK IN ('block','allow') | |
| daily_limit_minutes | int NULL | only meaningful when rule_type = 'allow' |
| is_active | boolean DEFAULT true | |
| created_at / updated_at | timestamptz | |

`UNIQUE (child_id, app_id)` — one rule per app per child (upsert target).
No `schedule_id` column yet; deferred until `schedules` exists (see `roadmap.md`).

### `usage_records`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| device_id | uuid FK → devices(id) ON DELETE CASCADE | |
| child_id | uuid FK → children(id) ON DELETE CASCADE | denormalized for dashboard/report query convenience |
| app_id | uuid FK → apps(id) ON DELETE CASCADE | |
| usage_date | date NOT NULL | child device's local day |
| duration_minutes | int NOT NULL DEFAULT 0 | cumulative for that day |
| last_updated_at | timestamptz DEFAULT now() | |

`UNIQUE (device_id, app_id, usage_date)` — upsert target. Sync always writes the child
device's cumulative total for the day; server takes `GREATEST(existing, incoming)`, which
makes replaying a queued offline batch idempotent by construction — no client-side dedup
needed. Index on `(child_id, usage_date)` for dashboard queries.

### `parent_refresh_tokens` / `device_refresh_tokens`
Two separate tables (kept distinct rather than one polymorphic table, for clean FKs at
this scale):
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| parent_id / device_id | uuid FK ... ON DELETE CASCADE | |
| token_hash | text NOT NULL | SHA-256; raw token is never persisted |
| issued_at, expires_at | timestamptz | |
| revoked_at | timestamptz NULL | |
| replaced_by_id | uuid NULL, self-referencing | rotation chain — lets reuse of an already-rotated token be detected and treated as a compromise signal |
| user_agent, ip_address | text NULL | parent tokens only, for audit |

### `audit_log`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| actor_type | text CHECK IN ('parent','device','system') | |
| actor_id | uuid NULL | |
| action | text NOT NULL | e.g. `pairing.code_generated`, `device.approved`, `rule.updated`, `auth.refresh_token_reused` |
| target_type, target_id | text/uuid NULL | |
| metadata | jsonb NULL | |
| created_at | timestamptz DEFAULT now() | |

This table's shape needs no future migrations — a generic event log is inherently
forward-compatible.

### `access_requests`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| child_id | uuid FK → children(id) ON DELETE CASCADE | |
| app_id | uuid FK → apps(id) ON DELETE CASCADE | |
| requested_minutes | int NOT NULL | |
| status | text NOT NULL DEFAULT 'pending' CHECK IN ('pending','approved','denied') | |
| resolved_minutes | int NULL | set on approval; null on deny. Currently always equals `requested_minutes` (the parent-app UI is one-tap approve/deny), but the column supports a partial grant if a future UI adds one |
| created_at, resolved_at, updated_at | timestamptz | `resolved_at` NULL until resolved |

Index on `(child_id, status)` for the "list pending" query. No `schedule_id`-style FK here —
a request is a one-off ask against the standing `app_rules` row, not a rule change itself.
Resolving a request does **not** touch `app_rules` or bump `children.config_version`; it's
purely informational for the parent today (see `roadmap.md`'s note on wiring an actual
override).

## Deferred tables (see `roadmap.md` for detail)

`schedules`, `website_rules`, `notifications`, `reports`, `categories` are documented but
not created in this slice's migrations.
