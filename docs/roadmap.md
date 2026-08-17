# Roadmap — deferred from the vertical slice

Everything below is from the full product spec but intentionally not built in this pass.
Each item notes how it slots into the existing schema/API without requiring a
rearchitecture — the slice's data model was chosen with these additions in mind.

### Schedules (school hours, bedtime, custom per-app/per-day windows) — built
`schedules` table (`id`, `child_id`, `name`, `days_of_week`, `start_time`, `end_time`,
`mode` = block/allow_only) plus a nullable `schedule_id` FK on `app_rules`. Two composed
uses: a blanket lockdown window (`mode`) and tying one specific rule to a window
(`schedule_id`) — see `docs/database-schema.md`'s `schedules` entry for exactly how they
combine (and the one combination that's a no-op: a BLOCK-type rule tied to an ALLOW_ONLY
schedule, since the blanket check already requires ALLOW). `EnforcementDecider`
(child-app) takes the current moment and every schedule (sent unfiltered - the device
decides activeness, not the backend, since it knows its own timezone) as new inputs.
Parent app: "Schedules & categories" screen (`ui/advancedrules/`) reachable from the
dashboard, plus an optional "tie to a schedule" chip row in the rule editor. **Not
built:** editing an existing schedule (delete and recreate instead — same minimal-CRUD
depth as pairing codes) and any UI exception list for BLOCK-mode schedules (they're an
unconditional lockdown in this pass).

### Website/content restrictions
A `website_rules` table mirroring `app_rules`' shape but keyed by domain instead of
`app_id` (`id`, `child_id`, `domain`, `rule_type`). Enforcement requires either DNS-level
filtering (a configured private DNS resolver — no traffic decryption) or Android's
`VpnService` as a local-only packet router for domain-level blocking (still no TLS
interception). Both are legitimate, disclosed mechanisms; neither was in scope for this
slice's enforcement mechanism (`UsageStatsManager`-based app blocking only). Still
deferred — the only item left in this file that touches enforcement mechanism itself
rather than being built on top of the existing app-blocking one.

### Category-level rules and limits — built
`category_rules` table (`child_id`, `category`, `daily_limit_minutes`); `apps.category` is
now actually populated, tagged via `UpsertRuleRequest.category` when a rule is set (no
separate category-management endpoint). Resolution order per `EnforcementDecider`: an
explicit per-app `dailyLimitMinutes` always wins; the category limit is only consulted as
a fallback when the per-app ALLOW rule leaves its own limit unset. **Scope limit** (see
`docs/database-schema.md`'s `category_rules` entry for the full reasoning): only applies
to apps that already have a standing rule, since the device only learns a package's
category via its synced `app_rules` row — an uncatalogued app stays unrestricted
regardless of category, same as an uncatalogued app always has been. Parent app: category
limits are managed from the same "Schedules & categories" screen as schedules.

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

### Reports (daily/weekly/monthly, charts) — built
`GET /children/{childId}/report?range=daily|weekly|monthly&anchor=YYYY-MM-DD` — pure
aggregation over `usage_records`, no schema change, no `reports` table (not needed since
nothing is pre-computed/cached — a candidate for later if PDF export or similar wants a
snapshot). Ranges are rolling windows ending at `anchor`, not calendar boundaries (see
`docs/database-schema.md`'s recurring note on why: no device-timezone signal
server-side). Parent app: bar-list breakdown by app on a new "Reports & insights" screen
(`ui/reports/`), no charting library added — plain `Box`-width bars are enough for this
scope.

### Smart insights — built
`GET /children/{childId}/insights` — rolling 7-day-vs-previous-7-day comparison and top-3
apps, computed from `usage_records`, no new tables. The "per category" angle from the
original idea folded into the same screen as Reports rather than becoming a separate
insights-by-category breakdown — category data is still thin in this slice (only tagged
when a parent sets one via the rule editor), so a per-category trend isn't meaningfully
different from the per-app one yet.

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
