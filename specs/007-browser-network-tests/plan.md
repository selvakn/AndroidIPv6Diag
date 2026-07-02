# Implementation Plan: Browser-Based Network Test Suite

**Branch**: `007-browser-network-tests` | **Date**: 2026-07-02 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/007-browser-network-tests/spec.md`

## Summary

Add a server-hosted browser diagnostics page that runs HTTP, HTTPS, ICMP-equivalent reachability, STUN, and TURN tests from the client browser context. Deliver mixed target selection (preconfigured defaults + optional custom targets), per-test timeouts with continue-on-failure behavior, session-local run history, and open unauthenticated access aligned with clarified scope.

## Technical Context

**Language/Version**: Go 1.25 for server, browser JavaScript (ES2020+) for client runtime  
**Primary Dependencies**: Go standard library HTTP stack; existing embedded web asset pattern; browser WebRTC APIs for STUN/TURN checks  
**Storage**: No new server persistence; browser session storage/in-memory for run history  
**Testing**: `go test ./...`; handler unit tests; manual browser quickstart checks  
**Target Platform**: Linux container/VPS server plus modern desktop/mobile browsers
**Project Type**: Web service with embedded static frontend  
**Performance Goals**: Full five-test run completes within 3 minutes in normal network conditions; each run reaches terminal status for all selected tests within 5 minutes  
**Constraints**: Public unauthenticated access; no rate limiting in this release; tests execute in browser client context only; per-test timeout required  
**Scale/Scope**: Single deployment serving diagnostics page and API endpoints for ad-hoc troubleshooting sessions

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution file is a placeholder template with no enforceable gates.  
Initial result: PASS.  
Post-design result: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/007-browser-network-tests/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── browser-diagnostics-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
server/
├── cmd/server/main.go                               # MODIFIED: route registration for browser diagnostics endpoints
├── internal/handler/
│   ├── browser_diagnostics.go                       # NEW: serves config endpoint for browser tests
│   ├── browser_diagnostics_test.go                  # NEW: config handler tests
│   ├── dashboard.go                                 # existing
│   └── turn_credentials.go                          # existing (optional TURN credentials source for browser tests)
├── web/
│   ├── assets.go                                    # MODIFIED: embed additional diagnostics HTML
│   ├── dashboard.html                               # existing
│   └── browser_diagnostics.html                     # NEW: browser-run test UI and orchestration
└── README.md                                        # MODIFIED: usage and configuration docs
```

**Structure Decision**: Extend the existing server-only module using the same embedded static asset pattern. Keep backend additions minimal (serving config + static page) and place diagnostic execution logic in browser JavaScript to satisfy client-context requirements.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
