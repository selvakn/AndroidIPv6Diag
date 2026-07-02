# Research: Browser-Based Network Test Suite

## Decision 1: Browser-only execution model for diagnostics
- **Decision**: Execute HTTP, HTTPS, ICMP-equivalent, STUN, and TURN checks from browser JavaScript and use server endpoints only for configuration and static page delivery.
- **Rationale**: The specification requires client-context execution to reflect end-user network conditions rather than server vantage point.
- **Alternatives considered**:
  - Server-side probing APIs: rejected because results represent server network path, not client path.
  - Native helper app: rejected because this feature explicitly targets browser-based access.

## Decision 2: ICMP-equivalent implemented as reachability probe
- **Decision**: Represent ICMP test as an ICMP-equivalent reachability probe using browser-permitted networking behavior (fetch/connectivity attempt with timeout and explicit limitation messaging).
- **Rationale**: Browsers cannot issue raw ICMP packets; equivalent behavior still provides actionable connectivity signal for users.
- **Alternatives considered**:
  - Raw ICMP via WebAssembly socket stacks: rejected due to browser security constraints and portability risk.
  - Drop ICMP test entirely: rejected because specification requires an ICMP-equivalent outcome.

## Decision 3: STUN/TURN checks via WebRTC ICE gathering
- **Decision**: Use WebRTC peer connection ICE candidate gathering against configured STUN/TURN servers to verify traversal capability from the browser.
- **Rationale**: WebRTC is the standardized browser mechanism for STUN/TURN interaction without additional plugins.
- **Alternatives considered**:
  - Custom UDP stack in browser: rejected due to lack of direct UDP API and unnecessary complexity.
  - Server-mediated TURN validation only: rejected because it would not validate client-side path behavior.

## Decision 4: Mixed target selection with explicit provenance labeling
- **Decision**: Provide operator-configured default targets and allow optional user-supplied targets in the same run; mark each result as default vs custom.
- **Rationale**: Matches clarification outcome and improves reproducibility while preserving exploratory troubleshooting.
- **Alternatives considered**:
  - Defaults only: rejected because custom destination troubleshooting is required.
  - Custom only: rejected because baseline repeatable checks are still needed.

## Decision 5: Timeout and failure strategy
- **Decision**: Apply per-test timeout and continue execution for remaining selected tests; record timed-out results as terminal outcomes.
- **Rationale**: Prevents one stalled test from blocking complete diagnostics and aligns with clarified behavior.
- **Alternatives considered**:
  - Global timeout only: rejected because it provides weaker per-test control.
  - No timeout: rejected due to unpredictable user wait times.

## Decision 6: Public access without throttling in this release
- **Decision**: Keep page and test execution unauthenticated and do not implement rate limiting in this feature release.
- **Rationale**: Directly matches clarifications and keeps scope focused on core diagnostic capabilities.
- **Alternatives considered**:
  - Authenticated access: rejected for this release by clarification.
  - Per-client throttling: intentionally deferred per clarification.
