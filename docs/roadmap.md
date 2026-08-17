# Roadmap — deferred from the vertical slice

Everything below is from the full product spec but intentionally not built in this pass.
Each item notes how it slots into the existing schema/API without requiring a
rearchitecture — the slice's data model was chosen with these additions in mind.

### Schedules (school hours, bedtime, custom per-app/per-day windows)
Add a `schedules` table (`id`, `child_id`, `name`, `days_of_week` (bitmask or array),
`start_time`, `end_time`, `mode` = block/allow-only). Add a nullable `schedule_id` FK
column to `app_rules` in a new migration (not pre-added now — a dangling nullable FK to a
nonexistent table is worse than adding it when needed). `MonitorForegroundService`'s
enforcement check gains one more input (current local time vs. active schedule) alongside
the existing block/allow/limit check.

### Website/content restrictions
A `website_rules` table mirroring `app_rules`' shape but keyed by domain instead of
`app_id` (`id`, `child_id`, `domain`, `rule_type`). Enforcement requires either DNS-level
filtering (a configured private DNS resolver — no traffic decryption) or Android's
`VpnService` as a local-only packet router for domain-level blocking (still no TLS
interception). Both are legitimate, disclosed mechanisms; neither was in scope for this
slice's enforcement mechanism (`UsageStatsManager`-based app blocking only).

### Category-level rules and limits
`apps.category` already exists as a stub column. A future `category_rules` table
(`child_id`, `category`, `daily_limit_minutes`) would apply alongside per-app rules; the
dashboard/enforcement logic would need a "most specific rule wins" resolution order
(per-app > category > default).

### Access requests ("child requests 20 more minutes") — built
End to end: `access_requests` + `access_overrides` tables, `POST /device/access-requests`
(device create), `GET /access-requests` (parent, list pending), `POST
/access-requests/{id}/resolve` (parent, approve/deny — approval writes a 24h override
row). Child app has an "Ask for N more minutes" button on `RestrictionScreen`
(`ui/restriction/RestrictionViewModel.kt`) and `EnforcementDecider` combines any active
override with the standing rule at decision time. Parent app has a "Time requests" inbox
reachable from the child list's top bar (`ui/accessrequests/`). See `docs/database-schema.md`
and `docs/api-spec.md` for the exact shapes.

**Deliberately not built:** the override window is a flat 24h from approval, not "the rest
of the device's local day" — the backend has no reliable signal for the device's timezone
at resolve time (`parents.timezone` is unused this slice). A real day-boundary override
would need the device to report its timezone (e.g. on pairing or heartbeat) before the
backend could compute a correct local-midnight expiry. Also not built: any UI telling the
child whether their request was approved/denied — the app just quietly becomes usable
again next sync; there's no push/notification path yet (see the separate "Notifications"
item below).

### Notifications
A `notifications` table (`id`, `parent_id`, `type`, `payload jsonb`, `read_at`,
`created_at`) plus FCM integration for push delivery (already planned as the dependency
in both apps' Gradle files, just not wired to a live send path yet). The event types listed
in the spec (limit reached, restricted-app attempt, device online/offline, new app
installed, etc.) all map to rows written by the corresponding backend service method,
picked up by a push-fanout job.

### Reports (daily/weekly/monthly, charts)
`usage_records` is already date-granular per app per device, so daily/weekly/monthly
rollups are pure aggregation queries against existing data — no schema change required for
the raw numbers. A `reports` table would only be needed if pre-computed/cached report
snapshots (e.g. for PDF export) are wanted later.

### Smart insights
Purely a read-side feature computed from `usage_records` history (e.g. week-over-week
percentage change per category) — no new tables needed, just new query/service code once
enough historical data and the category dimension exist.

### Multiple devices per child, additional parents per family, iOS
The schema already supports multiple `devices` rows per `child_id` (nothing in this slice
assumes one) and multiple `children` per `parent_id`. A second parent per family (shared
access) would need a `family_id` grouping concept above `parents`/`children` — not built
here since the spec's MVP scope is single-parent-per-account. iOS would consume the same
backend API; only `shared/`'s Kotlin DTOs would need an equivalent (Swift models or a KMP
migration of `shared/`) — the backend and its contract need no changes.

### AccessibilityService-based enforcement
Documented in `docs/architecture.md`'s enforcement design as the future upgrade path for
faster, more robust foreground-app detection than `UsageStatsManager` polling. Deferred
because it requires a materially heavier, explicitly-reviewed consent flow and a
declared use-case for Play Store policy compliance — out of scope for this slice per the
platform-policy constraint against requesting more than the minimum necessary sensitive
permissions.
