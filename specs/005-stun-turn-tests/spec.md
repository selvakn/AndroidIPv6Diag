# Feature Specification: Add STUN and TURN Tests

**Feature Branch**: `005-stun-turn-tests`  
**Created**: 2026-06-29  
**Status**: Draft  
**Input**: User description: "add STUN and TURN tests to the list. (it will work only if the server supports)."

## Clarifications

### Session 2026-06-29

- Q: Should STUN and TURN run in the default full diagnostics mode? → A: Yes, include both in default `ALL` diagnostics.
- Q: How should unsupported STUN/TURN be represented in result status? → A: `SKIPPED` with explicit reason `server unsupported`.
- Q: How should STUN/TURN support be determined? → A: Detect implicitly via direct STUN/TURN probe execution and protocol response interpretation.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run STUN/TURN diagnostics when supported (Priority: P1)

As a diagnostic user, I can run STUN and TURN tests as part of the diagnostic suite so I can verify whether NAT traversal and relay capabilities are available in my environment.

**Why this priority**: Adding STUN/TURN checks is the core feature request and directly expands diagnostic coverage.

**Independent Test**: Run diagnostics against a server that supports STUN/TURN and verify STUN/TURN results appear with clear pass/fail outcomes.

**Acceptance Scenarios**:

1. **Given** a configured target with STUN support, **When** diagnostics are executed, **Then** the STUN test runs and reports a result including captured ICE-style candidates.
2. **Given** a configured target with TURN support, **When** diagnostics are executed, **Then** the TURN test runs and reports a result.

---

### User Story 2 - Surface unsupported-server behavior clearly (Priority: P2)

As a diagnostic user, I can see clear unsupported or failed states for STUN/TURN tests when the target does not support them, so I can distinguish unsupported capability from generic connectivity failures.

**Why this priority**: The request explicitly notes that tests only work when the server supports these protocols; clarity prevents misinterpretation.

**Independent Test**: Run diagnostics against a server without STUN/TURN support and verify STUN/TURN results are marked clearly with actionable status/reason.

**Acceptance Scenarios**:

1. **Given** the target server does not support STUN, **When** diagnostics run, **Then** the STUN result is marked as unsupported or failed with a protocol-specific reason.
2. **Given** the target server does not support TURN, **When** diagnostics run, **Then** the TURN result is marked as unsupported or failed with a protocol-specific reason.

---

### User Story 3 - Include STUN/TURN in history and reporting views (Priority: P3)

As a dashboard or history viewer, I can review STUN/TURN outcomes in stored diagnostic sessions so I can compare protocol support across runs and environments.

**Why this priority**: Persisting and displaying results ensures new tests are operationally useful beyond live execution.

**Independent Test**: Complete a run with STUN/TURN results and verify those entries appear in local history and uploaded report detail views.

**Acceptance Scenarios**:

1. **Given** a completed diagnostic run with STUN/TURN checks, **When** session history or report detail is opened, **Then** STUN/TURN results are visible alongside existing tests.

---

### Edge Cases

- What happens when network access is present but STUN/TURN handshake times out?
- What happens when STUN is supported but TURN is unsupported on the same target?
- How does the system represent partial support (IPv4 supported, IPv6 unsupported) for STUN/TURN?
- How does the system behave when STUN/TURN configuration is missing while other tests are runnable?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST include STUN and TURN checks in the diagnostic test catalog and in the default `ALL` diagnostics run.
- **FR-002**: The system MUST execute STUN and TURN tests during diagnostic runs when corresponding server capability is available.
- **FR-003**: The system MUST classify STUN/TURN outcomes using existing result semantics, including `PASS`, `FAIL`, timeout-style failure states, and `SKIPPED` for unsupported server capability.
- **FR-004**: The system MUST provide protocol-specific failure or unsupported reasons for STUN/TURN outcomes, based on direct probe-response evidence rather than separate capability metadata endpoints.
- **FR-005**: The system MUST continue running and reporting other diagnostic tests even if STUN or TURN is unsupported or fails.
- **FR-006**: The system MUST persist STUN/TURN test results with the same retention and retrieval behavior as existing tests.
- **FR-007**: The system MUST include STUN/TURN test outcomes in report uploads and server-side report detail responses.
- **FR-008**: The system MUST display STUN/TURN test outcomes in user-facing results views where other protocol tests are shown.
- **FR-009**: The system MUST treat missing server support for STUN/TURN as a diagnostic outcome, not as an application crash or aborted run.
- **FR-010**: The system MUST determine STUN/TURN support by attempting STUN/TURN probe transactions against the configured target and interpreting returned protocol behavior.
- **FR-011**: The system MUST capture ICE-style candidate details from STUN interactions (at minimum host and server-reflexive candidates when available) and store them with STUN test results.
- **FR-012**: The system MUST include captured ICE candidate details in user-visible result/export surfaces and in uploaded report payloads for STUN entries.

### Key Entities *(include if feature involves data)*

- **StunTurnTestResult**: A diagnostic result entry for STUN or TURN containing protocol type, status, latency/response metadata when available, and failure/unsupported reason.
- **IceCandidateDetail**: Candidate metadata derived from STUN probing, including candidate type (for example host or server-reflexive) and candidate address/port text.
- **ServerCapabilityContext**: Run-specific metadata inferred from STUN/TURN probe responses that describes whether STUN and/or TURN capability was reachable for the target during execution.
- **DiagnosticSessionReport**: Existing session/report aggregate extended to carry STUN/TURN outcomes for local and cloud review.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of completed diagnostic runs include explicit STUN and TURN result entries.
- **SC-002**: For servers without STUN/TURN support, at least 95% of runs classify outcomes as `SKIPPED` with explicit unsupported reason (not generic unknown error).
- **SC-003**: 100% of uploaded reports from runs that execute STUN/TURN include those results in report detail.
- **SC-004**: Users can identify STUN/TURN support status from the results view within 10 seconds in acceptance testing scenarios.

## Assumptions

- STUN and TURN checks are additive and do not replace existing HTTP/HTTPS/ICMP/DNS checks.
- STUN and TURN are part of default full diagnostics unless a user explicitly selects a narrower test filter.
- Server environments may vary and can support STUN, TURN, both, or neither.
- Existing result status patterns can be reused for STUN/TURN without introducing a separate status framework.
- Current storage and report structures can be extended to include additional test-result entries without reducing compatibility for existing historical data.
