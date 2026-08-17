# Family Guard — Parental Control System (Vertical Slice)

A working end-to-end slice of an Android parental-control system: Parent App, Child App,
and a Kotlin/Ktor backend, sharing a common DTO module.

**Slice story:** parent signs up → creates a child profile → generates a pairing
QR/code → child app scans/enters it and registers the device → parent approves the
device → child app downloads its rule config → parent blocks/allows one app and sets a
daily limit for it → child app enforces that rule locally (works offline) and syncs usage
back → parent dashboard shows usage, device status, and last-sync time.

See `docs/` for the full design:
- `architecture.md` — system overview, data flow, **setup/build instructions**
- `database-schema.md` — tables and rationale
- `api-spec.md` — every endpoint, request/response shapes, auth model
- `pairing-security.md` — the pairing flow's security design in detail
- `roadmap.md` — everything from the full product spec deferred past this slice, and how
  it fits into this design when built later
- `testing-plan.md` — what's automated vs. manual, and in what order to run it

## Layout

```
shared/       plain Kotlin DTOs/enums/API paths, shared by all three other projects
backend/      Ktor + Postgres (Exposed + Flyway)
parent-app/   Compose app for the parent's phone
child-app/    Compose app for the child's phone
```

Each of `backend/`, `parent-app/`, `child-app/` is an independent Gradle build that pulls
in `shared/` via `includeBuild("../shared")`. Nothing has been compiled in the sandbox
this was built in (no Android SDK / JDK 17 / Gradle available there) — see
`docs/architecture.md` for how to build and run it on your own machine.
