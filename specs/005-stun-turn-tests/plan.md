# Implementation Plan: Add STUN and TURN Tests

**Branch**: `005-stun-turn-tests` | **Date**: 2026-06-29 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/005-stun-turn-tests/spec.md`

## Summary

Add STUN and TURN diagnostic checks to the existing Android test suite, include them in the default `ALL` run, and represent unsupported server capability as `SKIPPED` with explicit reason. Persist and surface these results through existing local history, upload payloads, and dashboard/report views without breaking current test flows.

## Technical Context

**Language/Version**: Go 1.24 (server), Kotlin/Android API 26+ (app)  
**Primary Dependencies**: Existing Android coroutines/OkHttp/Room stack; no new server dependencies required for generic test-result transport  
**Storage**: Existing Room `test_results` records (Android), SQLite JSON report storage (server)  
**Testing**: `go test ./...` (server), `mise exec -- ./gradlew assembleDebug` (Android build verification)  
**Target Platform**: Android API 26+ diagnostics app and Linux-hosted Go report server/dashboard  
**Project Type**: Mobile app + web-service extension in existing mono-repo  
**Performance Goals**: STUN/TURN checks complete within existing diagnostic run expectations (no major runtime regression)  
**Constraints**: STUN/TURN support must be inferred from direct probe response; unsupported must be `SKIPPED` with explicit reason; tests included in default `ALL` mode  
**Scale/Scope**: Extend existing protocol checks only; no new auth model or standalone capability API

## Constitution Check

Constitution is a placeholder template with no active, enforceable gates. No violations identified pre- or post-design.

## Project Structure

### Documentation (this feature)

```text
specs/005-stun-turn-tests/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── stun-turn-test-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
android/app/src/main/java/selvakn/ipv6diag/
├── data/model/TestResult.kt                     ← MODIFIED: add STUN/TURN test types
├── diagnostic/
│   ├── DiagnosticRunner.kt                      ← MODIFIED: include STUN/TURN in ALL flow
│   └── StunTurnTest.kt                          ← NEW: protocol probes and status mapping
├── ui/results/ResultsScreen.kt                  ← MODIFIED: no special handling needed; confirm rendering
└── export/SessionExporter.kt                    ← MODIFIED: verify STUN/TURN text export readability

specs/005-stun-turn-tests/*                      ← NEW/MODIFIED planning artifacts
CLAUDE.md                                        ← MODIFIED plan pointer for current feature
```

**Structure Decision**: Extend existing diagnostic test pipeline with a single new probe module and minor enum/runner updates; reuse existing persistence/upload/rendering pathways for test results.

## Complexity Tracking

No constitution violations requiring exception tracking.
