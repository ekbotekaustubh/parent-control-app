# Architecture

## Scope of this document

This describes the **vertical slice** that has been built: a complete, working path from
parent signup through paired-device rule enforcement and usage reporting. It intentionally
implements a small subset of the full product spec (see `roadmap.md` for what's deferred
and how it fits into this design) so that the hardest architectural problems — secure
pairing, rule synchronization, offline-tolerant enforcement — are solved end-to-end rather
than sketched across many partial features.

## System overview

```
┌─────────────────┐        HTTPS/JSON (JWT)        ┌──────────────────┐
│   Parent App     │ ──────────────────────────────▶│                  │
│ (Kotlin/Compose) │ ◀──────────────────────────────│   Backend API    │
└─────────────────┘                                 │  (Ktor + Postgres)│
                                                       │                  │
┌─────────────────┐        HTTPS/JSON (JWT)        │                  │
│   Child App      │ ──────────────────────────────▶│                  │
│ (Kotlin/Compose) │ ◀──────────────────────────────└──────────────────┘
└─────────────────┘
```

There is no direct phone-to-phone channel. Both apps only ever talk to the backend; the
backend is the sole source of truth and the only place authorization is enforced.

## Components

- **`shared/`** — plain Kotlin/JVM module (no Android, no KMP) containing the DTOs, enums,
  and API path constants used by all three other projects. Compiled directly into the
  backend and both Android apps via Gradle composite builds (`includeBuild`). This makes a
  request/response shape mismatch a **compile error**, not a runtime bug, across all three
  codebases.
- **`backend/`** — Ktor server, Postgres via Exposed, Flyway migrations. Owns all
  authorization decisions, token issuance/rotation, and is the only writer of the
  database.
- **`parent-app/`** — Compose app for the parent's phone. Auth, child/device management,
  rule editing, dashboard.
- **`child-app/`** — Compose app for the child's phone. Pairing, permission setup,
  background enforcement, usage sync.

## Data flow

**Rule authoring (parent → child):**
1. Parent edits a rule in `parent-app` → `PUT /children/{id}/apps/{packageName}/rule`.
2. Backend writes `app_rules`, bumps `children.config_version`.
3. Child app's `RuleSyncWorker` (periodic, WorkManager) calls `GET /device/config`,
   compares `configVersion`, and updates its local Room cache if changed.
4. `MonitorForegroundService` on the child device reads only the local Room cache to make
   enforcement decisions — it never blocks on network.

**Usage reporting (child → parent):**
1. `MonitorForegroundService` accrues foreground time per app into a local Room usage
   ledger, continuously, regardless of connectivity.
2. `UsageSyncWorker` (periodic, requires network) pushes queued cumulative snapshots to
   `POST /device/usage-sync`.
3. Backend upserts `usage_records` using `GREATEST(existing, incoming)` per
   `(device_id, app_id, usage_date)` — replaying a queued batch after being offline is
   always safe, no dedup logic needed anywhere.
4. Parent app reads `GET /children/{id}/dashboard`, a purpose-built read model joining
   device status, rules, and today's usage in one call.

## Why this shape

- **Backend-mediated, not peer-to-peer** — per the spec's explicit requirement, and because
  it's the only way to give the parent a consistent view of state when the child device is
  offline, and to make revocation actually work (revoking a device is a backend-side token
  action, not something either phone can override).
- **Composite Gradle builds, not one monorepo root** — Android (AGP, SDK, emulator tooling)
  and a headless JVM backend have different toolchain needs. Keeping `backend/`,
  `parent-app/`, and `child-app/` independently buildable avoids coupling their build
  configs while `shared/` still gives them a single compiled contract.
- **Local-first enforcement** — the child app always enforces from its local cache; the
  network is only ever used to keep that cache fresh and to report usage. This is what
  makes "offline enforcement" (spec §19) fall out of the design rather than being a special
  case.

## Setup instructions (build on your own machine — not attempted in this sandbox)

This sandbox has no Android SDK, no JDK 17, and very little free disk, so nothing here has
been compiled or run. To build and test on real hardware:

1. Install **Android Studio (current stable)** — bundles JDK 17 and lets you install the
   Android SDK/platform-tools/emulator through its SDK Manager.
2. Install **Docker** (for local Postgres, and for the backend's Testcontainers
   integration tests).
3. **Backend:**
   ```
   cd backend
   docker compose up -d db          # starts Postgres 16 on localhost:5432
   ./gradlew run                    # starts the Ktor server on :8080
   ./gradlew test                   # unit tests
   ./gradlew integrationTest        # Testcontainers-backed API tests (needs Docker)
   ```
4. **Android apps:** open `parent-app/` and `child-app/` as separate projects in Android
   Studio (they are independent Gradle builds, each pulling in `../shared` via
   `includeBuild`). Set `API_BASE_URL` in each app's `local.properties`
   (e.g. `http://10.0.2.2:8080/api/v1` for an emulator talking to a backend running on the
   host machine). Run each on a separate device/emulator to test pairing.
5. **Two-device pairing test:** run `parent-app` on device/emulator A and `child-app` on
   device/emulator B, both pointed at the same backend instance. Follow the flow in
   `pairing-security.md`.

## Package naming

All three Kotlin projects use the placeholder package root `com.familyguard.*`
(`com.familyguard.shared`, `com.familyguard.backend`, `com.familyguard.parent`,
`com.familyguard.child`). Rename via a project-wide find/replace before publishing if you
want a different name — nothing in the design depends on this specific string.
