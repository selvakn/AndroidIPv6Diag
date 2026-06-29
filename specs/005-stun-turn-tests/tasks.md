# Tasks: Add STUN and TURN Tests

**Input**: Design documents from `/specs/005-stun-turn-tests/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/stun-turn-test-contract.md`, `quickstart.md`

**Tests**: No explicit TDD requirement in spec; include build/test verification tasks at the end.

## Phase 1: Setup (Shared Infrastructure)

- [x] T001 Confirm planning artifacts are present in `specs/005-stun-turn-tests/` (`plan.md`, `research.md`, `data-model.md`, `quickstart.md`, `contracts/stun-turn-test-contract.md`)
- [x] T002 Update SPECKIT plan pointer in `CLAUDE.md` to `specs/005-stun-turn-tests/plan.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

- [x] T003 Extend test type enum with `STUN` and `TURN` in `android/app/src/main/java/selvakn/ipv6diag/data/model/TestResult.kt`
- [x] T004 Add/verify ignore coverage for local build caches (`.gradle-home/`, `.gocache/`) in `.gitignore`

**Checkpoint**: Core model and repo hygiene are ready for feature implementation.

---

## Phase 3: User Story 1 - Run STUN/TURN diagnostics when supported (Priority: P1) 🎯 MVP

**Goal**: Execute STUN/TURN checks as part of default diagnostics and produce concrete results.

**Independent Test**: Run default diagnostics and confirm STUN/TURN entries are emitted in results.

- [x] T005 [US1] Create STUN/TURN probe module in `android/app/src/main/java/selvakn/ipv6diag/diagnostic/StunTurnTest.kt`
- [x] T006 [US1] Integrate STUN/TURN checks into default `ALL` execution flow in `android/app/src/main/java/selvakn/ipv6diag/diagnostic/DiagnosticRunner.kt`
- [x] T007 [US1] Ensure graceful per-family fallback when endpoint resolution is missing in `android/app/src/main/java/selvakn/ipv6diag/diagnostic/DiagnosticRunner.kt`

**Checkpoint**: STUN/TURN run by default and generate deterministic test results.

---

## Phase 4: User Story 2 - Surface unsupported-server behavior clearly (Priority: P2)

**Goal**: Represent unsupported capability as `SKIPPED` with explicit reason.

**Independent Test**: Against non-supporting target, STUN/TURN appear as `SKIPPED` + `server unsupported`.

- [x] T008 [US2] Implement unsupported mapping (`SKIPPED` + reason) in `android/app/src/main/java/selvakn/ipv6diag/diagnostic/StunTurnTest.kt`
- [x] T009 [US2] Implement protocol-response-based support detection (no capability endpoint) in `android/app/src/main/java/selvakn/ipv6diag/diagnostic/StunTurnTest.kt`
- [x] T010 [US2] Align wording in spec-generated contract notes with runtime behavior in `specs/005-stun-turn-tests/contracts/stun-turn-test-contract.md`

**Checkpoint**: Unsupported cases are distinguishable from failures and consistent with clarified requirements.

---

## Phase 5: User Story 3 - Include STUN/TURN in history and reporting views (Priority: P3)

**Goal**: Ensure STUN/TURN results flow through persistence/export/UI/reporting paths.

**Independent Test**: Completed run shows STUN/TURN entries in results screen, exporter output, and uploaded report detail.

- [x] T011 [US3] Verify and adjust results rendering for new test types in `android/app/src/main/java/selvakn/ipv6diag/ui/results/ResultsScreen.kt`
- [x] T012 [US3] Verify and adjust text/json export readability for STUN/TURN in `android/app/src/main/java/selvakn/ipv6diag/export/SessionExporter.kt`
- [x] T013 [US3] Verify dashboard test row rendering remains compatible with STUN/TURN in `server/web/dashboard.html`
- [x] T018 [US3] Capture ICE-style STUN candidates in `android/app/src/main/java/selvakn/ipv6diag/diagnostic/StunTurnTest.kt`
- [x] T019 [US3] Persist ICE candidate details in Room model mappings in `android/app/src/main/java/selvakn/ipv6diag/data/db/Entities.kt` and `android/app/src/main/java/selvakn/ipv6diag/data/db/AppDatabase.kt`
- [x] T020 [US3] Render/export ICE candidate details in `android/app/src/main/java/selvakn/ipv6diag/ui/results/ResultsScreen.kt` and `android/app/src/main/java/selvakn/ipv6diag/export/SessionExporter.kt`

**Checkpoint**: STUN/TURN results are visible across local and cloud views.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T014 [P] Remove duplicate clarified requirements/entities in `specs/005-stun-turn-tests/spec.md` for consistency
- [x] T015 Run server verification `go test ./...` in `server/`
- [x] T016 Run Android verification `GRADLE_USER_HOME=/home/selva/projects/Lenovo/Mesh/AndroidIPv6Diag/.gradle-home mise exec -- ./gradlew assembleDebug` in `android/`
- [x] T017 Mark all completed tasks in `specs/005-stun-turn-tests/tasks.md`

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6
- US2 depends on probe primitives from US1.
- US3 depends on STUN/TURN results being present from US1/US2.

## Parallel Opportunities

- T014 can run in parallel with validation prep.
- T015 and T016 are independent verification tasks and can run in parallel after implementation.

## Implementation Strategy

### MVP First
1. Complete setup/foundational phases.
2. Deliver US1 STUN/TURN probe execution in default `ALL`.
3. Validate basic run output.

### Incremental Delivery
1. Add unsupported/status semantics (US2).
2. Confirm visibility/export/reporting flow (US3).
3. Run verification and finalize task tracking.
