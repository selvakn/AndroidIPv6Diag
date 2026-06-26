# Tasks: 464XLAT Enhanced Diagnostics

## Phase 1 — Foundational (Data Model & DB)

- [x] T001 Add NAT64_DISCOVERY, DNS64_VALIDATION, CLAT_QUALITY, PLAT_VERIFICATION to TestType enum in android/app/src/main/java/com/lenovo/mesh/ipv6diag/data/model/TestResult.kt
- [x] T002 Create android/app/src/main/java/com/lenovo/mesh/ipv6diag/data/model/XlatDiagnosticSummary.kt with all 5 model classes and enums
- [x] T003 Add XlatSummaryEntity to android/app/src/main/java/com/lenovo/mesh/ipv6diag/data/db/Entities.kt
- [x] T004 Create android/app/src/main/java/com/lenovo/mesh/ipv6diag/data/db/XlatSummaryDao.kt
- [x] T005 Update android/app/src/main/java/com/lenovo/mesh/ipv6diag/data/db/AppDatabase.kt — version 2, add migration, register XlatSummaryDao
- [x] T006 Add saveXlatSummary and getXlatSummary to android/app/src/main/java/com/lenovo/mesh/ipv6diag/data/repository/SessionRepository.kt

## Phase 2 — Diagnostic Engine (US1–US4)

- [x] T007 [P] [US1] Create android/app/src/main/java/com/lenovo/mesh/ipv6diag/diagnostic/Nat64Discovery.kt
- [x] T008 [P] [US2] Create android/app/src/main/java/com/lenovo/mesh/ipv6diag/diagnostic/Dns64Validation.kt
- [x] T009 [P] [US3] Create android/app/src/main/java/com/lenovo/mesh/ipv6diag/diagnostic/ClatQualityTest.kt
- [x] T010 [P] [US4] Create android/app/src/main/java/com/lenovo/mesh/ipv6diag/diagnostic/PlatVerification.kt
- [x] T011 [US4] Create android/app/src/main/java/com/lenovo/mesh/ipv6diag/diagnostic/XlatRunner.kt orchestrating all 4 sub-tests
- [x] T012 [US4] Modify android/app/src/main/java/com/lenovo/mesh/ipv6diag/diagnostic/DiagnosticRunner.kt — add XLAT_464 filter, wire XlatRunner, save XlatDiagnosticSummary

## Phase 3 — Export & UI (US5)

- [x] T013 [US5] Modify android/app/src/main/java/com/lenovo/mesh/ipv6diag/export/SessionExporter.kt — extend text and JSON exports with XLAT section
- [x] T014 [US5] Modify android/app/src/main/java/com/lenovo/mesh/ipv6diag/ui/results/ResultsScreen.kt — add XlatSummarySection composable

## Phase 4 — Build Verification

- [x] T015 Run mise exec -- sh -c "cd android && ./gradlew assembleDebug" and confirm BUILD SUCCESSFUL
