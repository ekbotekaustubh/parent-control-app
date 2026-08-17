# Pairing Security Design

## Design choice: opaque code, not a signed offline token

The QR code encodes a plaintext 8-character Crockford Base32 code (excludes ambiguous
characters `I`, `L`, `O`, `U`; ~40 bits of entropy) as a URI:
`familyguard://pair?code=XXXXXXXX`. The same string can be typed manually as a fallback
for devices without a working camera/scanner.

A self-contained signed JWT embedded in the QR (verifiable offline) was considered and
**rejected**: claiming a code always requires an online call to the backend anyway (the
child device has to register itself and receive real credentials), so a stateless signed
token would add a whole signing-key subsystem for zero actual benefit. All real security
lives server-side: hashing at rest, short TTL, an atomic single-use transition, and rate
limiting — not in the code's format.

## Flow

1. **Parent generates a code.** `POST /children/{childId}/pairing-codes`. Server
   generates the 8-char code, stores only `SHA-256(code)` in `pairing_sessions.code_hash`
   (plaintext is never persisted), sets `expires_at = now() + 10 minutes`, and invalidates
   any prior pending session for that child (enforced both in application logic and by a
   partial unique index `(child_id) WHERE status='pending'`).

2. **Child claims it.** `POST /pairing/claim`. The server resolves the session by hash
   lookup and performs the `pending → claimed` transition as **one atomic conditional
   update**, inside a transaction:
   ```sql
   UPDATE pairing_sessions
      SET status = 'claimed', claimed_by_device_id = :deviceId, claimed_at = now()
    WHERE code_hash = :hash AND status = 'pending' AND expires_at > now()
   RETURNING id;
   ```
   This is what makes the code genuinely single-use under a concurrent-claim race — a
   naive "SELECT then UPDATE" would let two simultaneous claims both see `pending` and
   both succeed. If the update returns zero rows, the request fails (`404`, `409`, or
   `410` depending on why).

   On success, the server creates the `devices` row (`status='pending_approval'`) and
   issues a **claim ticket**: a random 32-byte token, hashed at rest
   (`devices.pending_claim_ticket_hash`), 15-minute TTL. The claim ticket grants **zero
   business permissions** — it only allows the device to poll its own approval status and,
   later, redeem real tokens. It cannot read or write any rule/usage data.

3. **Child app polls for approval.** `GET /pairing/claim/{deviceId}/status` with header
   `X-Claim-Ticket: <ticket>`, roughly every 4 seconds, showing "Waiting for parent
   approval" in the UI.

4. **What "parent approves" concretely means.** `POST /devices/{deviceId}/approve`,
   authenticated as the parent. The server checks that the device's `child_id` resolves
   to a child owned by the calling parent's JWT `sub`, then performs a plain
   `pending_approval → approved` transition. **The child device has no path to trigger
   this itself, regardless of anything it sends** — approval is gated entirely by
   parent-JWT ownership, never by device-supplied data.

5. **Token exchange.** Once the poll reports `approved`, the child app calls
   `POST /devices/{deviceId}/token-exchange` with the claim ticket. The server verifies
   the ticket's hash and TTL and that `device.status == 'approved'`, then issues:
   - a **device refresh token** — opaque, hashed at rest, ~180-day expiry, rotated on
     every use, stored in `device_refresh_tokens`;
   - a short-lived (~20 min) **device access JWT** (`role=device`, `deviceId`,
     `childId` claims).
   The claim ticket is invalidated immediately (single-use) regardless of outcome.

6. **Ongoing auth.** The device behaves like any authenticated client from here:
   `Authorization: Bearer <deviceAccessToken>`, refreshed via
   `POST /devices/token/refresh` using the same rotate-and-detect-reuse pattern as parent
   tokens (see `api-spec.md`). If a revoked/already-used device refresh token is replayed,
   treat it as a compromise signal: revoke the entire device session and require
   re-pairing — do not attempt silent recovery.

7. **Revocation.** `POST /devices/{deviceId}/revoke` (parent-only) revokes every refresh
   token for that device and sets `status='revoked'`. The device's next call to any
   authenticated endpoint gets `401`. There is deliberately no self-service path back to
   `approved` from `revoked` — re-pairing from scratch is required, so a compromised or
   lost device can't silently regain access.

## Replay/reuse protection summary

| Artifact | Protection |
|---|---|
| Pairing code | single-use (atomic transition), 10-min TTL, hashed at rest, one active per child |
| Claim ticket | single-use, 15-min TTL, hashed at rest, zero business scope |
| Device refresh token | rotated every use, reuse ⇒ full session revocation |
| Parent refresh token | rotated every use, reuse ⇒ full session revocation + audit log |
| `/pairing/claim` endpoint | rate-limited per IP to blunt brute-forcing the 8-char code space |

## Confirmation UX

Both apps show explicit state at each step (per the spec's requirement for "clear
confirmation on both devices"): the parent sees the generated code/QR and, once the child
claims it, a pending-approval entry to approve; the child app shows "waiting for parent
approval" → "paired!" once the poll reports `approved` and token exchange succeeds.
