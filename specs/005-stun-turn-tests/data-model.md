# Data Model: Add STUN and TURN Tests

## Existing Entity Extensions

### `TestType` (enum)

- Add values:
  - `STUN`
  - `TURN`
- Purpose: Represent new protocol checks in the same lifecycle as existing `HTTP`, `HTTPS`, `ICMP`, `DNS`, and 464XLAT-related entries.

### `TestResult`

- No new mandatory fields required.
- Existing fields used by STUN/TURN:
  - `testType` (`STUN` or `TURN`)
  - `addressFamily` (`IPv4` or `IPv6`)
  - `status` (`PASS`, `FAIL`, `SKIPPED`, `ABORTED`)
  - `latencyMs` (probe round-trip time when available)
  - `failureReason` (protocol-specific reason including unsupported/auth-required context)
  - `resolvedAddress` (target IP used for probe when resolved)
- **New optional field**: `iceCandidates` (`List<String>`)
  - Includes ICE-style candidate strings derived from STUN, such as host and server-reflexive candidates.

## Derived Capability Context

### `ServerCapabilityContext` (derived, not standalone table)

- Inferred from STUN/TURN probe outputs:
  - `stunSupported` (boolean-like from result status/reason)
  - `turnSupported` (boolean-like from result status/reason)
- Candidate-related detail:
  - `hostCandidate` and `srflxCandidate` values are derived and stored in STUN `iceCandidates`.
- Stored implicitly via `TestResult` rows and reconstructed during report/history rendering.

## Persistence Impact

- Room `test_results` table stores `testType` as string; adding enum values is backward-compatible for new records.
- Room `test_results` table adds `ice_candidates` text column (JSON list) for candidate persistence.
- Existing historical records remain valid and untouched.

## Upload/Report Impact

- Uploaded `test_results` array now includes `STUN`/`TURN` entries using same JSON shape.
- Server stores test results as JSON blob; no schema migration needed for this feature.

## State Notes

1. Diagnostic run resolves target and executes base checks plus STUN/TURN under `ALL`.
2. Each STUN/TURN probe emits `TestResult` with explicit status and reason.
3. Results are persisted locally and included in upload payloads.
4. Dashboard/history readers display STUN/TURN entries alongside other tests.
