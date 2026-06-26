# Tasks: Android IPv6 Diagnostic Tool

**Input**: Design documents from `specs/001-android-ipv6-diag/`  
**Branch**: `001-android-ipv6-diag`  
**Date**: 2026-06-26

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared in-progress dependencies)
- **[Story]**: User story label (US1–US5)
- Exact file paths are included in every task description

---

## Phase 1: Setup (Project Initialization)

**Purpose**: Create both project skeletons with correct toolchain configuration. Server and Android setup are fully parallel.

- [x] T001 Create `server/` directory structure: `cmd/server/`, `internal/handler/`, `internal/listener/`, `bin/`
- [x] T002 [P] Initialize Go module at `server/go.mod` with module path `github.com/lenovo/mesh/ipv6diag-server`, Go 1.24
- [x] T003 [P] Create `server/Makefile` with targets: `build`, `build-arm64`, `run`, `test`, `vet`, `clean` (see quickstart.md for exact commands)
- [x] T004 [P] Create Android project at `android/` using Android Studio or `gradle init`: package `com.lenovo.mesh.ipv6diag`, minSdk 26, targetSdk 35, Kotlin 2.1, Jetpack Compose enabled
- [x] T005 [P] Create `android/gradle/libs.versions.toml` version catalog with all library versions from research.md: OkHttp 4.12.0, Room 2.6.1, kotlinx.serialization 1.7.3, kotlinx.coroutines 1.9.0, Compose BOM 2025.04.01, Lifecycle 2.8.x, Navigation Compose 2.8.x
- [x] T006 [P] Configure `android/app/build.gradle.kts`: apply version catalog, enable `kotlinx.serialization` plugin, Room KSP processor, Compose compiler, set minSdk/targetSdk

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure both components need before any user story can be implemented. Server listener core and Android network binder are independent and can proceed in parallel.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Server Foundation

- [x] T007 Create `server/internal/listener/dual_stack.go`: function `CreateListeners(httpAddr, http6Addr, httpsAddr, https6Addr, certFile, keyFile string)` that returns 4 `net.Listener` instances (IPv4 HTTP, IPv6 HTTP, IPv4 HTTPS, IPv6 HTTPS); HTTPS listeners disabled if cert/key paths are empty
- [x] T008 Create `server/internal/handler/diag.go`: `DiagHandler` struct with `ServeHTTP` that reads `net.Conn` remote/local addr, classifies `address_family` as `"IPv4"` or `"IPv6"` (treating `::ffff:x.x.x.x` as IPv4), sets `protocol` from a `bool isTLS` field, returns JSON matching `contracts/server-api.md`
- [x] T009 [P] Create `server/internal/handler/health.go`: `HealthHandler` that returns `200 text/plain "ok"`
- [x] T010 Create `server/cmd/server/main.go`: parse flags (`--http-addr`, `--http6-addr`, `--https-addr`, `--https6-addr`, `--cert`, `--key`, `--version`), call `CreateListeners`, create `http.ServeMux` with `/diag` and `/health`, start all listeners with `http.Serve` in goroutines, block on OS signal

### Android Foundation

- [x] T011 Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/data/model/` data classes: `DiagnosticSession.kt`, `TestResult.kt`, `NetworkInfo.kt`, `ServerEndpoint.kt` — fields per `data-model.md`; annotate with `@Serializable` for export
- [x] T012 [P] Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/data/db/AppDatabase.kt`: Room database class with `DiagnosticSessionEntity`, `TestResultEntity` mapped from data classes; include `NetworkInfo` as JSON-serialized `@TypeConverter`
- [x] T013 [P] Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/data/db/DiagnosticSessionDao.kt` and `TestResultDao.kt`: queries for insert, get-by-id, list-all (ordered by timestamp DESC), delete-oldest (for 50-session cap)
- [x] T014 Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/data/repository/SessionRepository.kt`: `saveSession(session: DiagnosticSession)` (inserts + enforces 50-session cap by deleting oldest when count > 50), `getAllSessions(): Flow<List<DiagnosticSession>>`, `getSessionById(id: UUID): DiagnosticSession?`
- [x] T015 Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/diagnostic/CellularNetworkBinder.kt`: uses `ConnectivityManager.requestNetwork()` with `NetworkCapabilities.TRANSPORT_CELLULAR`; exposes `withCellularNetwork(block: suspend (Network) -> T): T` coroutine that awaits the callback and times out with a clear error if no cellular network is available within 10 seconds
- [x] T016 [P] Add required permissions to `android/app/src/main/AndroidManifest.xml`: `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`
- [x] T017 Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/MainActivity.kt` with Compose `NavHost` wiring routes: `home`, `results/{sessionId}`, `networkInfo`, `history`, `settings`
- [x] T018 [P] Create `android/app/src/main/res/values/config.xml` with `<string name="default_server_hostname">` placeholder (to be filled at deploy time)

**Checkpoint**: Server binary can be built (`make build`). Android project compiles. Cellular network binding is testable.

---

## Phase 3: User Story 1 — Run Full IPv6 Connectivity Diagnostic (P1) 🎯 MVP

**Goal**: Single-tap "Run All Tests" that executes HTTP, HTTPS, ICMP, and DNS tests over cellular against both IPv4 and IPv6 server addresses and shows pass/fail per test.

**Independent Test**: Connect device to LTE, tap "Run All Tests," verify results appear for all 8 test combinations (4 protocols × 2 address families) within 30 seconds.

- [x] T019 [US1] Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/diagnostic/HttpTest.kt`: function `runHttpTest(network: Network, serverIp: String, port: Int, addressFamily: AddressFamily): TestResult` — uses OkHttp with `network.socketFactory` as the socket factory, times the request, parses `/diag` JSON response to confirm `server_confirmed_family`
- [x] T020 [P] [US1] Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/diagnostic/HttpsTest.kt`: same as HttpTest but targets port 443 and uses HTTPS; sets `protocol = "HTTPS"` in the result
- [x] T021 [P] [US1] Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/diagnostic/IcmpTest.kt`: `runIcmpTest(targetIp: String, addressFamily: AddressFamily): TestResult` — uses `Runtime.exec(arrayOf("ping", "-c", "3", "-W", "5", ip))` for IPv4 and `ping6` for IPv6; parses RTT and packet loss from stdout; returns SKIPPED if binary not found
- [x] T022 [P] [US1] Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/diagnostic/DnsTest.kt`: `runDnsTest(network: Network, hostname: String): List<TestResult>` — on API 29+: use `android.net.DnsResolver.query(network, hostname, TYPE_A, ...)` and `TYPE_AAAA` separately; on API 26–28: fall back to `InetAddress.getAllByName` after `network.bindSocket`; returns one TestResult per record type; sets `resolvedAddress` from response
- [x] T023 [US1] Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/diagnostic/DiagnosticRunner.kt`: `runAllTests(network: Network, endpoint: ServerEndpoint): DiagnosticSession` — resolves endpoint's IPv4 and IPv6 addresses, runs all 4 protocol tests × 2 address families sequentially (or coroutine-parallelized within a single `Network` scope), collects TestResults, saves session via SessionRepository; registers a `ConnectivityManager` network callback to detect mid-run network changes and abort with `ABORTED` + reason "network changed during test"
- [x] T024 [US1] Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/ui/home/HomeScreen.kt`: Compose screen with "Run All Tests" button, progress indicator during test run, navigation to ResultsScreen on completion; shows error if mobile data is off
- [x] T025 [US1] Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/ui/results/ResultsScreen.kt`: displays each TestResult row with test type label, address family badge (IPv4 / IPv6 / XLAT), pass/fail status chip, latency value, and failure reason (if any); groups by protocol
- [x] T026 [US1] Validate Phase 3: build server (`make build`), deploy to dual-stack host, run full test suite on LTE device, confirm all 8 results appear and `server_confirmed_family` matches the address family tested

---

## Phase 4: User Story 2 — Inspect Network Interface and Address Details (P2)

**Goal**: Dedicated screen showing all active interfaces, IPv4/IPv6 addresses, CLAT interface and addresses, DNS resolver IPs, and CLAT detection status.

**Independent Test**: Navigate to Network Info screen on an LTE device; verify cellular interface, assigned addresses, DNS servers, and CLAT status are displayed.

- [x] T027 [US2] Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/diagnostic/NetworkInfoCollector.kt`: `collect(network: Network, connectivityManager: ConnectivityManager): NetworkInfo` — reads `LinkProperties.linkAddresses`, `LinkProperties.dnsServers`, checks for CLAT interface (names starting with `clat` or `v4-`), identifies synthetic IPv4 from CLAT, detects global unicast vs link-local IPv6, reads `mobileDataEnabled` from `TelephonyManager`, captures API level
- [x] T028 [US2] Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/ui/network/NetworkInfoScreen.kt`: Compose screen with sections: "Cellular Interface" (name, IPv4, all IPv6 addresses), "464XLAT / CLAT" (present/absent badge, CLAT interface name, synthetic IPv4, NAT64 prefix), "DNS Resolvers" (list of IP + hostname for each resolver), "Mobile Data" (enabled/disabled indicator); add "Network Info" navigation entry to HomeScreen
- [x] T029 [P] [US2] Update `DiagnosticRunner.kt` to capture `NetworkInfo` via `NetworkInfoCollector` at session start and embed it in `DiagnosticSession`
- [x] T030 [US2] Validate Phase 4: on a 464XLAT carrier SIM, open Network Info screen and confirm CLAT interface is detected and synthetic IPv4 is displayed

---

## Phase 5: User Story 3 — Run Individual Protocol Tests (P2)

**Goal**: Allow the user to select a specific protocol (DNS, ICMP, HTTP/HTTPS) and run only those tests.

**Independent Test**: Select "DNS Only," verify only DNS results appear; select "ICMP Only," verify only ICMP results appear.

- [x] T031 [US3] Add protocol selector UI to `android/app/src/main/java/com/lenovo/mesh/ipv6diag/ui/home/HomeScreen.kt`: segmented control or chip group with options "All", "HTTP/HTTPS", "ICMP", "DNS"; selected filter persists across runs in this session
- [x] T032 [US3] Update `DiagnosticRunner.kt`: add `runTests(network: Network, endpoint: ServerEndpoint, filter: Set<TestType>): DiagnosticSession` overload that skips test categories not in `filter`; `TestType` enum: HTTP, HTTPS, ICMP, DNS
- [x] T033 [US3] Validate Phase 5: select "ICMP" filter, run tests, confirm only ICMPv4 and ICMPv6 results appear in ResultsScreen

---

## Phase 6: User Story 4 — Configure Diagnostic Server Endpoint (P3)

**Goal**: Allow the user to enter a custom server hostname/IP; revert to default when cleared.

**Independent Test**: Enter a custom hostname in Settings, run tests, confirm all test URLs use the custom hostname.

- [x] T034 [US4] Create Room entity `ServerEndpointEntity` in `android/app/src/main/java/com/lenovo/mesh/ipv6diag/data/db/`: table `server_endpoints` per data-model.md; add `ServerEndpointDao` with get-default, upsert, clear-custom methods
- [x] T035 [US4] Add `getDefaultEndpoint()` and `saveCustomEndpoint(hostname: String)` to `SessionRepository.kt`; on first launch, seed the default endpoint from `config.xml`
- [x] T036 [US4] Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/ui/settings/SettingsScreen.kt`: text field for custom server hostname, "Save" and "Reset to Default" buttons, shows current active endpoint, reachability indicator (calls `/health` when hostname is saved)
- [x] T037 [US4] Validate Phase 6: enter custom hostname, run tests, confirm custom hostname is used; clear and confirm default is restored

---

## Phase 7: User Story 5 — View and Share Test Results (P3)

**Goal**: History list of past sessions; export each session as plain text or JSON via Android share sheet.

**Independent Test**: Run two test sessions, open History, verify both appear; tap Share on one session, confirm plain text and JSON options are offered via the share sheet.

- [x] T038 [US5] Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/ui/history/HistoryScreen.kt`: Compose screen listing past sessions (timestamp, server, pass/fail summary); tap a session to open ResultsScreen with that session's data; navigation entry added to main nav
- [x] T039 [US5] Create `android/app/src/main/java/com/lenovo/mesh/ipv6diag/export/SessionExporter.kt`: `exportAsText(session: DiagnosticSession): String` — formats a multi-section plain-text report (header, network info, per-test results, footer with timestamp and endpoint); `exportAsJson(session: DiagnosticSession): String` — serializes via `kotlinx.serialization` Json
- [x] T040 [US5] Add "Share Results" button to `ResultsScreen.kt`: opens a bottom sheet letting the user pick "Plain Text" or "JSON"; on selection calls `SessionExporter` and fires `Intent.ACTION_SEND` with the appropriate MIME type (`text/plain` or `application/json`)
- [x] T041 [P] [US5] Add "Copy to Clipboard" option alongside share in `ResultsScreen.kt`: copies the plain-text report via `ClipboardManager`
- [x] T042 [US5] Validate Phase 7: run tests, open history, export as both formats, verify all fields (network info, per-test results, timestamp, endpoint) are present in each format

---

## Final Phase: Polish & Cross-Cutting Concerns

**Purpose**: Network change safety, session cleanup, server completeness, UX robustness.

- [x] T043 Implement network change abort in `DiagnosticRunner.kt`: register `ConnectivityManager.NetworkCallback.onLost` for the cellular network; when fired mid-run, mark all `RUNNING` tests as `ABORTED` with reason "network changed during test," mark session `ABORTED`, save partial session, navigate user to ResultsScreen showing partial results with the abort banner
- [x] T044 [P] Implement session retention cap in `SessionRepository.saveSession()`: after insert, query count; if > 50, delete the oldest session(s) until count ≤ 50; use a database transaction for atomicity
- [x] T045 [P] Add `--version` flag handling to `server/cmd/server/main.go`: embed version string via `go build -ldflags "-X main.version=<ver>"` and print to stdout then exit
- [x] T046 [P] Complete `server/Makefile` with all targets from quickstart.md: `build` (amd64), `build-arm64` (cross-compile `GOARCH=arm64`), `run` (HTTP-only), `test`, `vet`, `clean`; add `.PHONY` declarations
- [x] T047 Add graceful shutdown to `server/cmd/server/main.go`: on `SIGINT`/`SIGTERM`, close all listeners with a 5-second drain timeout before exit
- [x] T048 [P] Add mobile data disabled guard in `CellularNetworkBinder.kt`: check `TelephonyManager.isDataEnabled()` before requesting the cellular network; return a descriptive error if data is off so the UI can show "Mobile data is disabled — enable it to run tests"
- [x] T049 [P] Add IPv6 link-local detection in `NetworkInfoCollector.kt`: distinguish link-local addresses (`fe80::/10`) from global unicast; `hasNativeIPv6` must be `true` only for global unicast addresses (per FR-006)
- [x] T050 [P] Add DNS server hostname reverse-resolution in `NetworkInfoCollector.kt`: for each DNS server IP, attempt `InetAddress.getByAddress().canonicalHostName` (best-effort, timeout 2s); populate `dnsServerNames` list in `NetworkInfo`
- [x] T051 [P] Add server error handling in `HttpTest.kt` / `HttpsTest.kt`: if server returns non-200 or malformed JSON on `/diag`, return `FAIL` with reason "unexpected server response: <status>"; distinguish network-level failure (connection refused, timeout) from server-level failure
- [x] T052 Conduct end-to-end validation: test on three network configurations — (1) native dual-stack LTE, (2) 464XLAT carrier, (3) IPv4-only carrier — and confirm SC-002 (100% accurate scenario detection); document results in `specs/001-android-ipv6-diag/validation-notes.md`

---

## Dependencies (User Story Completion Order)

```
Phase 1 (Setup)
    └── Phase 2 (Foundation)
            ├── Phase 3 (US1: Full Diagnostic Suite)  ← MVP
            │       ├── Phase 4 (US2: Network Info)   ← can start in parallel with US3
            │       └── Phase 5 (US3: Protocol Filter)
            │               ├── Phase 6 (US4: Custom Server)  ← independent of US2/US3
            │               └── Phase 7 (US5: History & Export)
            └── Final Phase (Polish) ← after all user stories
```

US2 and US3 can be implemented in parallel after US1 is complete (they depend on Foundation + Server, not on each other).

US4 and US5 can be implemented in parallel with each other after US1.

---

## Parallel Execution Opportunities

Within a phase, tasks marked `[P]` can be worked simultaneously:

| Phase | Parallel Groups |
|-------|----------------|
| Phase 1 | T002+T003 (server setup) ‖ T004+T005+T006 (Android setup) |
| Phase 2 | T007+T008+T009+T010 (server) ‖ T011–T018 (Android, mostly independent) |
| Phase 3 | T019 (HTTP) ‖ T020 (HTTPS) ‖ T021 (ICMP) ‖ T022 (DNS) then T023→T024→T025 |
| Phase 4 | T027→T028, T029 parallel |
| Final | T043–T052 mostly parallel after US story phases |

---

## Implementation Strategy

**MVP (Phase 1–3)**: Delivers the complete diagnostic test suite and server binary. A tester with a dual-stack SIM and the server deployed can validate all core IPv6/464XLAT scenarios.

**Increment 2 (Phase 4–5)**: Adds network info inspection and protocol filtering — quality-of-life features for field engineers.

**Increment 3 (Phase 6–7)**: Custom server config and result history/export — lab/automation-readiness features.

**Increment 4 (Final Phase)**: Robustness, edge case handling, and cross-network validation.

---

## Task Summary

| Phase | Story | Tasks | Notes |
|-------|-------|-------|-------|
| Phase 1: Setup | — | T001–T006 | 6 tasks, fully parallel across server/Android |
| Phase 2: Foundation | — | T007–T018 | 12 tasks; server and Android independent |
| Phase 3: US1 | P1 | T019–T026 | 8 tasks; 3 test runners parallel |
| Phase 4: US2 | P2 | T027–T030 | 4 tasks |
| Phase 5: US3 | P2 | T031–T033 | 3 tasks |
| Phase 6: US4 | P3 | T034–T037 | 4 tasks |
| Phase 7: US5 | P3 | T038–T042 | 5 tasks |
| Final: Polish | — | T043–T052 | 10 tasks; mostly parallel |
| **Total** | | **52 tasks** | |
