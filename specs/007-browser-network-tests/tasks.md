# Tasks: Browser-Based Network Test Suite

**Input**: Design documents from `/specs/007-browser-network-tests/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/browser-diagnostics-contract.md`, `quickstart.md`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare server/web scaffolding for browser diagnostics feature.

- [X] T001 Add browser diagnostics asset embedding entry in `server/web/assets.go`
- [X] T002 Add browser diagnostics route wiring placeholders in `server/cmd/server/main.go`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core backend support required by all user stories.

- [X] T003 Create browser diagnostics config handler in `server/internal/handler/browser_diagnostics.go`
- [X] T004 [P] Add unit tests for config handler method/shape in `server/internal/handler/browser_diagnostics_test.go`
- [X] T005 Register config/page endpoints for HTTP and HTTPS muxes in `server/cmd/server/main.go`
- [X] T006 Document configuration environment variables in `server/README.md`

**Checkpoint**: Browser diagnostics page and config API are served and testable.

---

## Phase 3: User Story 1 - Run Core Connectivity Tests from Browser (Priority: P1) 🎯 MVP

**Goal**: Execute HTTP/HTTPS/ICMP-equivalent/STUN/TURN from browser and show per-test outcomes.

**Independent Test**: Open `/browser-diagnostics`, run default suite, and verify every selected test reaches a terminal state with status and reason/evidence.

- [X] T007 [US1] Implement browser diagnostics page UI skeleton in `server/web/browser_diagnostics.html`
- [X] T008 [US1] Implement HTTP/HTTPS/ICMP-equivalent test runners with per-test timeout in `server/web/browser_diagnostics.html`
- [X] T009 [US1] Implement STUN/TURN checks via WebRTC ICE gathering in `server/web/browser_diagnostics.html`
- [X] T010 [US1] Implement continue-on-failure orchestration and status rendering in `server/web/browser_diagnostics.html`

**Checkpoint**: US1 can run all five test types and show terminal per-test results.

---

## Phase 4: User Story 2 - Use Server-Hosted Web Page for Remote Troubleshooting (Priority: P2)

**Goal**: Serve diagnostics page from server with mixed targets (defaults + optional custom).

**Independent Test**: Access page from remote client network, run defaults and custom targets, and verify result provenance labels.

- [X] T011 [US2] Implement config-driven default target loading in `server/web/browser_diagnostics.html`
- [X] T012 [US2] Implement custom target input and validation for enabled mode in `server/web/browser_diagnostics.html`
- [X] T013 [US2] Show target provenance label (`default`/`custom`) in results and run summary in `server/web/browser_diagnostics.html`

**Checkpoint**: US2 supports server-hosted page with mixed target selection behavior.

---

## Phase 5: User Story 3 - Compare Repeated Test Runs During Investigation (Priority: P3)

**Goal**: Keep session-local run history with timestamps and outcomes.

**Independent Test**: Execute multiple runs and verify distinct history entries and details are viewable.

- [X] T014 [US3] Add run history model and session persistence logic in `server/web/browser_diagnostics.html`
- [X] T015 [US3] Render run history list/detail comparison view in `server/web/browser_diagnostics.html`

**Checkpoint**: US3 provides client-session run history comparison.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation and documentation updates.

- [X] T016 [P] Add browser diagnostics usage notes to `specs/007-browser-network-tests/quickstart.md`
- [X] T017 Run and fix `go test ./...` in `server/`
- [X] T018 Mark completed tasks and verify checklist format in `specs/007-browser-network-tests/tasks.md`

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6.
- US2 depends on foundational config endpoint and US1 page scaffold.
- US3 depends on US1 execution outputs.

## Parallel Opportunities

- T004 can run parallel with T003 implementation completion review.
- T016 can run while implementation stabilizes.

## Parallel Example: User Story 1

```bash
Task: "Implement HTTP/HTTPS/ICMP-equivalent test runners with per-test timeout in server/web/browser_diagnostics.html"
Task: "Implement STUN/TURN checks via WebRTC ICE gathering in server/web/browser_diagnostics.html"
```

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phases 1-2.
2. Deliver Phase 3 and validate full suite execution.

### Incremental Delivery
1. Add US2 mixed target behavior.
2. Add US3 run history comparison.
3. Finalize docs/tests in polish phase.
