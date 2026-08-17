# Testing Plan

## Fully automatable, no devices needed (backend)

Run with `./gradlew test` (unit) and `./gradlew integrationTest` (Testcontainers,
requires Docker) inside `backend/`.

**Unit tests:**
- `AuthService` — password hashing/verification, JWT sign/verify, refresh-token rotation,
  and reuse-detection (replaying an already-rotated token revokes the chain).
- `PairingService` — the pairing-code and claim-ticket state machines, expiry handling,
  and a **concurrency test** that fires simultaneous claim attempts at the same code
  against a real database connection to assert the atomic
  `UPDATE ... WHERE status='pending'` really only lets one caller win.
- `RuleService` — upsert semantics, `config_version` bump on change.
- `UsageService` — the `GREATEST`-upsert is idempotent under replay (same batch applied
  twice produces the same stored value as applying it once).
- `AccessRequestService` — create requires an existing app-catalog entry, resolve is
  ownership-checked and atomic (double-resolve throws `Conflict`, not a silent second
  write), approve without an explicit `resolvedMinutes` falls back to the requested
  amount, approving creates an active `access_overrides` row and denying does not.

**Integration tests** (`testApplication { }` + **Testcontainers PostgreSQL** — not H2,
because the schema relies on real `gen_random_uuid()`, `jsonb`, and `ON CONFLICT`
semantics that H2 doesn't faithfully emulate), covering full HTTP flows end-to-end:
- signup → login → refresh → logout
- create child → generate pairing code → claim → approve → token-exchange → fetch config
- set a rule → device usage-sync → dashboard reflects the synced usage and rule

These integration tests are the automated stand-in for a physical-device end-to-end test
and should exist and pass **before** any Android code is written against the API, so the
contract is validated in isolation first.

## Partially automatable (Android, single emulator, no pairing partner needed)

- Room DAO instrumented tests (in-memory Room) for the rule cache, usage ledger, sync
  queue, and override cache (`CachedOverrideDaoTest` — including expiry filtering)
  upsert logic.
- Pure-JVM ViewModel tests (`kotlinx-coroutines-test`, fake repositories) for both apps,
  including `RestrictionViewModel` (child-app, request-more-time flow) and
  `AccessRequestsViewModel` (parent-app, the approve/deny inbox).
- `MockWebServer` contract tests verifying Retrofit request/response shapes — lower risk
  here specifically because `shared/`'s DTOs already make shape drift a compile error
  rather than a runtime bug.
- The actual block/allow/over-limit decision is extracted into a pure
  `EnforcementDecider` class with **zero Android framework dependency**
  (`child-app/.../domain/EnforcementDecider.kt`), so it can be table-driven-tested in
  isolation from the untestable `UsageStatsManager`/foreground-service plumbing around it.
- The `PACKAGE_USAGE_STATS` special-access permission can be granted non-interactively for
  instrumented tests via:
  ```
  adb shell appops set <package> android:get_usage_stats allow
  ```
  which makes more of the enforcement path automatable than it first appears.

## Requires two physical/emulated Android devices (manual/exploratory, not CI)

- Full pairing flow through camera QR scan (physical devices recommended — emulator
  camera feeds are unreliable for scanning; use the manual code-entry fallback on
  emulators instead).
- Real foreground-detection timing and the restriction-screen "flash then cover" behavior
  described honestly in `architecture.md`.
- Offline/reconnect drain of the sync queue via airplane-mode toggling (a single emulator
  is sufficient for this one — no second device strictly required).
- Doze/background-kill resilience: needs real elapsed hours for a faithful test, or a
  rough simulation via `adb shell dumpsys deviceidle force-idle`.
- Device reboot: confirm `BootCompletedReceiver` re-schedules `MonitorForegroundService`
  and the sync workers.
- Time-zone/day-boundary: change the device's timezone/date and confirm `usage_date`
  rolls over correctly and yesterday's ledger doesn't bleed into today's limit check.

## Recommended order

1. Backend unit + integration suite green (Testcontainers) — validates the whole API
   contract in isolation, before any Android code exists.
2. Child-app Room/ViewModel/`EnforcementDecider` unit tests.
3. Parent-app equivalent ViewModel/repository unit tests.
4. Manual two-device pass through the full vertical-slice story (signup → pairing →
   rule → enforcement → sync → dashboard).

By stage 4 you should only be debugging integration glue (network config, permission
prompts), not business logic — that's already covered by stages 1–3.
