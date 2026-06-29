# Research: Add STUN and TURN Tests

## STUN Support Detection

**Decision**: Send a STUN Binding Request to the configured test host and classify support from protocol-conformant response behavior.  
**Rationale**: This directly validates runtime support and avoids introducing a separate server capability API.  
**Alternatives considered**:
- Capability metadata endpoint (`/capabilities`) — rejected because it adds backend contract surface and can drift from actual runtime behavior.
- Static configuration toggle — rejected because it is error-prone and not evidence-based.

## TURN Support Detection

**Decision**: Send a TURN Allocate request to the configured target and interpret responses (`success` or auth-challenge errors) as support evidence.  
**Rationale**: TURN commonly requires authentication; receiving protocol-valid error responses still proves TURN service support.  
**Alternatives considered**:
- Treat non-success as unsupported — rejected because auth-required deployments would be misclassified.
- Full authenticated relay session in v1 — rejected as out of scope for diagnostics-only capability checks.

## Unsupported Status Semantics

**Decision**: Map unsupported STUN/TURN to `SKIPPED` with reason `server unsupported`.  
**Rationale**: This distinguishes missing capability from transport-level failure and matches clarified spec intent.  
**Alternatives considered**:
- Use `FAIL` — rejected because it implies service malfunction rather than unavailable feature.
- Use `ABORTED` — rejected because no run interruption occurred.

## Default Test Inclusion

**Decision**: Include STUN and TURN checks in default `ALL` diagnostic mode.  
**Rationale**: Ensures consistent coverage and aligns with clarified behavior from `/speckit-clarify`.  
**Alternatives considered**:
- Separate optional toggle — rejected because it can hide protocol capability gaps.
- Partial default inclusion (STUN only) — rejected as incomplete for relay diagnostics.

## Data Transport and Display Compatibility

**Decision**: Reuse existing generic `TestResult` structures, persistence, upload payloads, and UI rendering.  
**Rationale**: Current model is test-type agnostic and can carry STUN/TURN without schema redesign.  
**Alternatives considered**:
- Introduce protocol-specific report entities — rejected as unnecessary complexity for current scope.
