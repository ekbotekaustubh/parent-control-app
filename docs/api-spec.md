# API Specification

Base path: `/api/v1`. All bodies are JSON. All timestamps are ISO-8601 UTC.

## Auth model

Two independent JWT `Authentication` providers in Ktor, distinguished by a `role` claim:

- `auth-parent` — issued to parents, claims: `sub` (parent id), `role=parent`.
- `auth-device` — issued to paired devices, claims: `sub` (device id), `role=device`,
  `childId`.

Every route picks exactly one via `authenticate("auth-parent") { ... }` or
`authenticate("auth-device") { ... }`. **Ownership is always re-derived server-side from
the JWT claims — a parent's or device's own id/childId is never trusted from a
client-supplied path/body parameter.** This is the anti-IDOR rule for the whole API.

Error envelope (uniform, via Ktor `StatusPages`):
```json
{ "error": { "code": "INVALID_CREDENTIALS", "message": "Email or password is incorrect." } }
```
No stack traces or internal detail are ever returned to the client.

Rate limiting applies to `/auth/*` and `/pairing/claim`.

---

## Auth (parent)

### `POST /auth/signup`
Request: `{ "email": "...", "password": "...", "displayName": "..." }`
Response `201`: `{ "parentId": "...", "accessToken": "...", "refreshToken": "...", "expiresIn": 1200 }`
Password is BCrypt-hashed before storage; the plaintext is never logged.

### `POST /auth/login`
Request: `{ "email": "...", "password": "..." }`
Response `200`: `{ "accessToken": "...", "refreshToken": "...", "expiresIn": 1200 }`
`401 INVALID_CREDENTIALS` on mismatch (same error for "no such email" and "wrong
password" — do not leak which one).

### `POST /auth/refresh`
Request: `{ "refreshToken": "..." }`
Response `200`: `{ "accessToken": "...", "refreshToken": "..." }`
Rotates the refresh token: the old one is marked used (`replaced_by_id` set). If a
token that is already revoked/used is replayed, the **entire rotation chain** for that
parent is revoked and an `audit_log` entry (`auth.refresh_token_reused`) is written — this
is the standard refresh-token-reuse-detection pattern and treats replay as a likely
compromise signal.

### `POST /auth/logout`
Request: `{ "refreshToken": "..." }` → `204`. Revokes that token.

---

## Children (parent-authenticated)

### `POST /children`
Request: `{ "name": "...", "birthYear": 2016 }` → `201` Child object.

### `GET /children`
→ `200 [Child]`, scoped to the caller's own children only.

### `GET /children/{childId}`
→ `200 Child`, or `404` if it doesn't exist **or** isn't owned by the caller (never
`403` — a `403` would confirm the id exists under someone else's account).

---

## Pairing

### `POST /children/{childId}/pairing-codes`
Parent-only, ownership-checked. → `201 { "code": "XXXXXXXX", "expiresAt": "..." }`.
Auto-invalidates any prior pending code for that child (partial unique index enforces
this at the DB level too).

### `POST /pairing/claim`
**Unauthenticated** — the device has no credentials yet. Protected instead by the code's
short TTL, single-use atomic claim, hashing at rest, and IP rate limiting.
Request: `{ "code": "XXXXXXXX", "deviceModel": "...", "osVersion": "...", "appVersion": "..." }`
Response `200`: `{ "deviceId": "...", "claimTicket": "...", "claimTicketExpiresIn": 900 }`
`410 CODE_EXPIRED` / `409 CODE_ALREADY_CLAIMED` / `404 CODE_NOT_FOUND` as appropriate.

### `GET /pairing/claim/{deviceId}/status`
Header: `X-Claim-Ticket: <ticket>`. Polled by the child app (~4s interval) while waiting.
→ `200 { "status": "pending_approval" | "approved" | "revoked" }`.

### `POST /devices/{deviceId}/approve`
Parent-only; ownership-checked (device's `child_id` must resolve to a child owned by the
caller). → `200` Device object. This is the *only* path that flips a device to
`approved` — nothing the device itself sends can trigger it.

### `POST /devices/{deviceId}/token-exchange`
Request: `{ "claimTicket": "..." }` (no auth header — the claim ticket is the credential).
Succeeds only if the ticket hash matches, hasn't expired, and `device.status == 'approved'`.
→ `200 { "deviceAccessToken": "...", "deviceRefreshToken": "...", "expiresIn": 1200 }`.
Invalidates the claim ticket (single-use) on success.

### `POST /devices/token/refresh`
Request: `{ "refreshToken": "..." }` → `200 { "accessToken": "...", "refreshToken": "..." }`.
Same rotation + reuse-detection semantics as `/auth/refresh`; reuse revokes the whole
device session and requires re-pairing.

### `GET /devices?childId=...`
Parent-only. → `200 [Device]`.

### `POST /devices/{deviceId}/revoke`
Parent-only. → `204`. Revokes all of that device's refresh tokens and sets
`status='revoked'`. The device's next authenticated call gets `401`; there is no
self-service path back — re-pairing is required.

---

## Rules (parent-authenticated, ownership-checked)

### `PUT /children/{childId}/apps/{packageName}/rule`
Request: `{ "ruleType": "block" | "allow", "dailyLimitMinutes": 45, "category": "social", "scheduleId": "..." }`
(upsert). `category` and `scheduleId` are both optional. `category` is sticky - omit it to
leave the app's existing category (if any) untouched; it tags `apps.category`, not the
rule itself. `scheduleId` is full-replace like every other field here - omit it (or pass
`null`) to make the rule always-apply; passing one ties the rule to that schedule's window
(`404 SCHEDULE_NOT_FOUND` if it doesn't belong to this child). → `200` AppRule object.
Bumps `children.config_version`.

### `GET /children/{childId}/rules`
→ `200 [AppRule]`. Callable by the owning parent, or by a device whose JWT `childId`
claim matches (the child app uses this indirectly via `/device/config`, not this route
directly, but it's available for parity). `AppRule` shape: `{ "id", "childId",
"packageName", "displayName", "ruleType", "dailyLimitMinutes", "isActive", "category",
"scheduleId" }`.

### `DELETE /children/{childId}/apps/{packageName}/rule`
→ `204`. Bumps `children.config_version`.

---

## Category rules (parent-authenticated, ownership-checked)

See `docs/database-schema.md`'s `category_rules` table for the scope limit (only applies
to apps that already have a standing unlimited ALLOW rule).

### `PUT /children/{childId}/categories/{category}/rule`
Request: `{ "dailyLimitMinutes": 60 }` (upsert on `(childId, category)`) → `200`
CategoryRule object: `{ "id", "childId", "category", "dailyLimitMinutes" }`. Does **not**
bump `children.config_version` — `GET /device/config` always includes the current
`categoryRules` regardless.

### `GET /children/{childId}/category-rules`
Parent-only. → `200 [CategoryRule]`.

### `DELETE /children/{childId}/categories/{category}/rule`
→ `204`.

---

## Schedules (parent-authenticated, ownership-checked)

Create/list/delete only - no update endpoint; delete and recreate to change one, same
minimal-CRUD depth as pairing codes.

### `POST /children/{childId}/schedules`
Request: `{ "name": "Bedtime", "daysOfWeek": ["MON","TUE","WED","THU","FRI"], "startTime":
"21:00", "endTime": "07:00", "mode": "block" | "allow_only" }` → `201` Schedule object:
`{ "id", "childId", "name", "daysOfWeek", "startTime", "endTime", "mode", "isActive" }`.
`400 INVALID_DAYS_OF_WEEK` if `daysOfWeek` is empty, `400 INVALID_TIME_RANGE` if
`startTime == endTime`. `startTime > endTime` is valid and means an overnight window.

### `GET /children/{childId}/schedules`
→ `200 [Schedule]`.

### `DELETE /children/{childId}/schedules/{scheduleId}`
→ `204`. Any `app_rules` row tied to this schedule is detached (`schedule_id` set to
`null`), not deleted.

---

## Device-facing (device-authenticated; `deviceId`/`childId` always taken from the JWT)

### `GET /device/config`
→ `200 { "childId": "...", "rules": [AppRule], "overrides": [AccessOverride],
"categoryRules": [CategoryRule], "schedules": [Schedule], "configVersion": 7,
"syncIntervalSeconds": 900, "serverTimeUtc": "..." }`
`overrides` is every currently-active (not-yet-expired) grant from an approved access
request for this child — see the Access requests section below. `AccessOverride` shape:
`{ "packageName", "extraMinutes", "expiresAt" }`. `categoryRules` and `schedules` are sent
**unfiltered** (every row for the child, not just "active right now") — the device
evaluates schedule activeness itself using its own local clock; see
`docs/database-schema.md`'s `schedules` entry. The child app's `EnforcementDecider`
combines all of these with the matching `AppRule` at decision time; none of them replace
or change `rules` itself.

### `POST /device/usage-sync`
Request: `{ "events": [ { "packageName": "...", "usageDate": "2026-08-16", "durationMinutes": 32 } ] }`
→ `200 { "acceptedCount": 3, "serverTimeUtc": "..." }`
Each event is upserted with `GREATEST(existing, incoming)` on
`(device_id, app_id, usage_date)` — safe to resend/replay. Updates the device's
`last_sync_at` and `last_seen_at`.

### `POST /device/heartbeat`
Request: `{}` → `200 { "ok": true, "configVersion": 7 }`. Cheap liveness ping so the
parent's "online/offline" status is accurate even between real syncs.

### `POST /device/access-requests`
"Child asks for more time" (see `docs/roadmap.md`'s original "Access requests" writeup,
now built — `docs/database-schema.md`'s `access_requests` table).
Request: `{ "packageName": "...", "requestedMinutes": 15 }` → `201` AccessRequest object.
`404 APP_NOT_FOUND` if the package has never had a rule set for this child (the apps
catalog only gains an entry via a rule upsert — see `RuleService.findOrCreateApp`).

---

## Access requests (parent-authenticated)

### `GET /access-requests`
→ `200 [AccessRequest]`, pending requests across **every** child owned by the caller (not
`childId`-scoped, unlike the rest of the parent API) — matches the parent-app's single
cross-child "Time requests" inbox screen.

### `POST /access-requests/{requestId}/resolve`
Ownership-checked via the request's child. Request: `{ "approve": true, "resolvedMinutes":
10 }` (`resolvedMinutes` optional, defaults to the request's `requestedMinutes`; ignored
when `approve: false`). → `200` AccessRequest object. `409 ACCESS_REQUEST_ALREADY_RESOLVED`
if called twice (atomic conditional update, same pattern as the pairing-claim race guard in
`pairing-security.md`).

Approving writes an `access_overrides` row (`docs/database-schema.md`) valid for 24 hours
from approval — a rolling window, not "the rest of the device's local day", since the
backend doesn't know the device's timezone at resolve time. It does **not** touch the
standing `app_rules` row or bump `children.config_version` — the override is picked up by
the next `GET /device/config` poll (≤15 minutes, `child-app/work/RuleSyncWorker.kt`), and
`EnforcementDecider` combines it with the standing rule at decision time rather than the
backend rewriting the rule itself.

`AccessRequest` object shape: `{ "id", "childId", "packageName", "displayName",
"requestedMinutes", "status": "pending"|"approved"|"denied", "resolvedMinutes",
"createdAt", "resolvedAt" }`.

---

## Dashboard (parent-authenticated, ownership-checked)

### `GET /children/{childId}/dashboard`
→ `200`:
```json
{
  "device": { "status": "approved", "online": true, "lastSeenAt": "...", "lastSyncAt": "..." },
  "rules": [ { "packageName": "...", "ruleType": "allow", "dailyLimitMinutes": 45 } ],
  "usageToday": [
    { "packageName": "com.google.android.youtube", "displayName": "YouTube",
      "durationMinutes": 32, "dailyLimitMinutes": 45, "isOverLimit": false }
  ]
}
```
A purpose-built read model for this one screen (avoids N+1 client calls). Documented as a
candidate to decompose into more general queries once Reports needs broader date-range
aggregation than the dedicated endpoint below.

---

## Reports & insights (parent-authenticated, ownership-checked)

Both are pure aggregation over `usage_records` — no new tables (`docs/roadmap.md`). Ranges
are **rolling windows ending at `anchor`** (default today), not calendar week/month
boundaries, for the same reason as `access_overrides`: no reliable device-local-timezone
signal server-side.

### `GET /children/{childId}/report?range=daily|weekly|monthly&anchor=YYYY-MM-DD`
`range` defaults to `weekly` if omitted; `anchor` defaults to today. `daily` = just
`anchor`; `weekly` = the 7 days ending `anchor`; `monthly` = the 30 days ending `anchor`.
→ `200 { "range": "weekly", "startDate": "...", "endDate": "...", "totalMinutes": 210,
"byApp": [ { "packageName", "displayName", "totalMinutes" } ], "byDay": [ { "date",
"totalMinutes" } ] }`. `400 INVALID_RANGE` for anything other than the three listed
values, `400 INVALID_ANCHOR` for a malformed date.

### `GET /children/{childId}/insights`
→ `200 { "currentWeekMinutes": 100, "previousWeekMinutes": 50,
"weekOverWeekChangePercent": 100.0, "topApps": [ { "packageName", "displayName",
"minutes" } ], "generatedAt": "..." }`. "This week"/"last week" are rolling 7-day windows,
not calendar weeks. `weekOverWeekChangePercent` is `null` when `previousWeekMinutes` is 0
(no baseline to compute a percentage against). `topApps` is at most the top 3 apps by
minutes this week.
