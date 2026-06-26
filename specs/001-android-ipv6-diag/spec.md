# Feature Specification: Android IPv6 Diagnostic Tool

**Feature Branch**: `001-android-ipv6-diag`  
**Created**: 2026-06-26  
**Status**: Draft  
**Input**: User description: "we are building a diagnostic android app, to debug IPv6 connectivity with LTE / cellular networks. the android should be able to do all possible tests, to validate direct IPv6, as well as 464XLAT. It should do tests with the following protocols: http, https, ICMP, DNS, etc. We should have two components, one to host the server with ipv4 and ipv6 address, and the other is the android app itself."

## Clarifications

### Session 2026-06-26

- Q: Which DNS resolver(s) should DNS tests use? → A: System/carrier-assigned resolver only (validates real 464XLAT/DNS64 path). The app must also display the active DNS resolver name and IP address.
- Q: Who should be able to reach and use the diagnostic server? → A: Private/internal deployment — access controlled at network level (firewall/VPN); no application-level authentication required.
- Q: Should the app persist diagnostic session history on the device? → A: Yes — store the last N sessions locally on-device (no cloud sync); user can review and compare past runs.
- Q: What format should the exported diagnostic report use? → A: Both plain text and JSON — user selects format at share time via the Android share sheet.
- Q: What should the app do if the active cellular interface changes or drops during a test run? → A: Abort in-progress tests and mark them as failed with a "network changed during test" reason; do not silently mix results from two network states.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run Full IPv6 Connectivity Diagnostic (Priority: P1)

A network engineer or developer connects an Android device to an LTE/cellular network and launches the diagnostic app to determine whether the device has working IPv6 connectivity. The app runs a comprehensive test suite covering direct IPv6, 464XLAT translation, and multiple protocols (HTTP, HTTPS, ICMP, DNS), then presents a clear pass/fail report for each test.

**Why this priority**: This is the core value proposition of the tool — a single-tap diagnostic that surfaces all IPv6-related connectivity issues on cellular networks. Without this, the app delivers no value.

**Independent Test**: Can be fully tested by launching the app on a device with an LTE SIM, tapping "Run All Tests," and verifying that results appear for each protocol and address family within a reasonable time. Delivers actionable diagnostic data independently.

**Acceptance Scenarios**:

1. **Given** the device is connected to an LTE network with native IPv6, **When** the user taps "Run All Tests," **Then** the app runs HTTP, HTTPS, ICMP, and DNS tests against the diagnostic server's IPv6 address and reports pass/fail for each.
2. **Given** the device is connected to an LTE network that uses 464XLAT (IPv4-only core with IPv6 prefix), **When** the user taps "Run All Tests," **Then** the app detects the CLAT interface, runs both IPv4-mapped and native IPv6 tests, and correctly identifies the 464XLAT path.
3. **Given** the device has no IPv6 connectivity, **When** the tests run, **Then** each IPv6-specific test reports a clear failure with a human-readable reason (e.g., "No IPv6 address assigned," "ICMPv6 unreachable").
4. **Given** any test fails during the suite, **When** the report is shown, **Then** the remaining tests still complete and are reported independently.

---

### User Story 2 - Inspect Network Interface and Address Details (Priority: P2)

Before or after running tests, the user wants to inspect what network addresses and interfaces are currently active on the device — including whether a CLAT (464XLAT client-side) interface is present, what IPv4 and IPv6 addresses are assigned, and which interface carries cellular traffic.

**Why this priority**: Many IPv6 issues stem from misconfigured or missing addresses. Surfacing interface/address information independently of protocol tests lets users quickly identify the root cause without running full tests.

**Independent Test**: Can be tested by navigating to the "Network Info" screen and verifying that all active interfaces, assigned IPv4/IPv6 addresses, and CLAT detection status are displayed. Delivers standalone diagnostic value.

**Acceptance Scenarios**:

1. **Given** the device has an active cellular interface, **When** the user opens the Network Info screen, **Then** the app lists all active interfaces with their IPv4 and IPv6 addresses, prefix lengths, and interface names.
2. **Given** 464XLAT is active, **When** the Network Info screen is shown, **Then** the CLAT interface is identified and labeled separately with its synthetic IPv4 address and corresponding IPv6 prefix.
3. **Given** the device has both Wi-Fi and cellular active, **When** the Network Info screen is displayed, **Then** the cellular interface is clearly distinguished from Wi-Fi.

---

### User Story 3 - Run Individual Protocol Tests (Priority: P2)

The user wants to run a specific subset of tests (e.g., only DNS, or only ICMP) rather than the full suite, to isolate a suspected issue quickly.

**Why this priority**: Full suite runs can take time and produce noise. Targeted protocol tests help users zero in on specific failure modes.

**Independent Test**: Can be tested by selecting a single protocol (e.g., DNS) from the test menu and verifying that only DNS tests execute and results are shown.

**Acceptance Scenarios**:

1. **Given** the user selects "DNS Only" from the test options, **When** the test runs, **Then** only DNS resolution tests are performed against the server's IPv4 and IPv6 addresses, and results are shown.
2. **Given** the user selects "ICMP Only," **When** the test runs, **Then** ping/ICMPv6 echo tests are performed and round-trip times and packet loss are reported.
3. **Given** the user selects "HTTP/HTTPS," **When** the test runs, **Then** HTTP and HTTPS connectivity tests are performed against both the IPv4 and IPv6 server endpoints.

---

### User Story 4 - Configure Diagnostic Server Endpoint (Priority: P3)

The user wants to point the app at a custom diagnostic server (self-hosted) rather than a default one, so they can test within a specific network environment or lab setup.

**Why this priority**: The two-component design (server + app) requires the app to know where the server is. Default configuration covers most cases, but lab and enterprise users need custom targeting.

**Independent Test**: Can be tested by entering a custom server hostname/IP in settings and verifying that all subsequent tests use that server address.

**Acceptance Scenarios**:

1. **Given** the user opens Settings and enters a custom server hostname, **When** tests are run, **Then** all protocol tests target the custom server's IPv4 and IPv6 addresses.
2. **Given** the user clears the custom server setting, **When** tests are run, **Then** the app reverts to the default server endpoint.
3. **Given** the custom server is unreachable, **When** tests are run, **Then** the app reports the server as unreachable and does not crash.

---

### User Story 5 - View and Share Test Results (Priority: P3)

After running tests, the user wants to save or share the diagnostic report — as a text log or structured output — to send to a network team, file a bug report, or compare across multiple devices or sessions.

**Why this priority**: Diagnostic tools are only useful if results can be communicated. Sharing and export enables collaboration and issue tracking.

**Independent Test**: Can be tested by running tests, then tapping "Share Results" and verifying that a structured report (containing all test results and network info) is exported or shared via standard Android share intent.

**Acceptance Scenarios**:

1. **Given** tests have completed, **When** the user taps "Share Results," **Then** the app generates a text report containing all test results, device network info, timestamps, and server endpoint, and opens the Android share sheet.
2. **Given** the user wants to copy results, **When** they select "Copy to Clipboard," **Then** the same report is copied as plain text.

---

### Edge Cases

- If the active cellular interface changes or drops mid-test, all in-progress tests are aborted and marked as failed with reason "network changed during test." Tests that completed before the change retain their results.
- How does the app handle a server that has an IPv6 address but the device cannot reach it due to firewall rules?
- What if the device has an IPv6 address assigned but it is a link-local address only (not globally routable)?
- What if DNS resolves IPv6 but ICMP to that address fails — is the distinction reported clearly?
- What if 464XLAT is active but the CLAT interface has no route to the diagnostic server?
- What if the diagnostic server is reachable only via IPv4 (no IPv6) and the device is IPv6-only?

## Requirements *(mandatory)*

### Functional Requirements

**Android App**

- **FR-001**: The app MUST run diagnostic tests over the active cellular (LTE/mobile data) interface, not Wi-Fi, when both are available.
- **FR-002**: The app MUST test connectivity using HTTP (port 80) against both the server's IPv4 and IPv6 addresses.
- **FR-003**: The app MUST test connectivity using HTTPS (port 443) against both the server's IPv4 and IPv6 addresses.
- **FR-004**: The app MUST perform ICMP echo (ping) and ICMPv6 echo tests to the server's IPv4 and IPv6 addresses respectively.
- **FR-005**: The app MUST perform DNS resolution tests using the system/carrier-assigned resolver only — querying A records (IPv4) and AAAA records (IPv6) for the server's hostname — and report the resolved addresses. The system resolver is used to correctly capture DNS64 synthesis behavior on 464XLAT networks.
- **FR-006**: The app MUST detect whether the device has a native IPv6 address assigned to its cellular interface (global unicast, not link-local).
- **FR-007**: The app MUST detect whether 464XLAT (CLAT) is active by identifying a CLAT network interface or synthesized IPv4 address.
- **FR-008**: The app MUST display per-test results including pass/fail status, latency (where measurable), and a failure reason for failed tests.
- **FR-009**: The app MUST allow the user to run all tests at once or select individual protocol tests.
- **FR-010**: The app MUST display active network interfaces with their assigned IPv4 and IPv6 addresses and CLAT interface details.
- **FR-010a**: The app MUST display the active DNS resolver name (if available) and IP address(es) in the Network Info screen.
- **FR-011**: The app MUST allow the user to configure a custom diagnostic server hostname or IP address.
- **FR-012**: The app MUST export or share completed diagnostic sessions in two formats — plain text (human-readable, suitable for emails and bug reports) and JSON (machine-parseable, suitable for automation and CI tooling). The user selects the format at share time via the Android share sheet.
- **FR-013**: The app MUST complete all tests in a full suite run and present results even if individual tests fail.
- **FR-014**: The app MUST display the timestamp and active server endpoint in every test report.
- **FR-015b**: If the active cellular network interface changes or is lost during a test run, the app MUST abort all in-progress tests, mark them as failed with the reason "network changed during test," and preserve results from tests that already completed.
- **FR-015a**: The app MUST store completed diagnostic sessions locally on the device and allow the user to view a history of past sessions. The number of retained sessions MUST be bounded (maximum 50 sessions) to limit storage use. No cloud sync is required.

**Diagnostic Server**

- **FR-015**: The server MUST be reachable on both an IPv4 address and an IPv6 address simultaneously. Access is restricted at the network level (firewall/VPN); the server requires no application-level authentication.
- **FR-016**: The server MUST serve HTTP responses on port 80 for both IPv4 and IPv6 connections.
- **FR-017**: The server MUST serve HTTPS responses on port 443 for both IPv4 and IPv6 connections, with a valid TLS certificate.
- **FR-018**: The server MUST respond to ICMP echo requests (IPv4 ping) and ICMPv6 echo requests (IPv6 ping).
- **FR-019**: The server's hostname MUST have both A (IPv4) and AAAA (IPv6) DNS records so DNS resolution tests can validate both address families.
- **FR-020**: The server MUST return a diagnostic response payload that identifies which address family the connection arrived on (IPv4 or IPv6), so the app can verify end-to-end path correctness.

### Key Entities

- **DiagnosticSession**: A single run of one or more tests. Contains a timestamp, selected server endpoint, list of TestResults, and device NetworkInfo snapshot. Sessions are persisted locally; up to 50 sessions are retained (oldest discarded when limit is reached).
- **TestResult**: The outcome of a single protocol/address-family test. Includes test type (HTTP/HTTPS/ICMP/DNS), address family (IPv4/IPv6/464XLAT), pass/fail status, latency, and failure reason.
- **NetworkInfo**: Snapshot of the device's active network interfaces at the time of the test. Includes interface names, assigned addresses, CLAT interface presence, and which interface is active for cellular.
- **ServerEndpoint**: The configured diagnostic server, described by hostname, resolved IPv4 address, and resolved IPv6 address.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A complete full-suite diagnostic run (all protocols, both address families) completes within 30 seconds on an active LTE connection.
- **SC-002**: The app correctly identifies native IPv6, 464XLAT, and IPv4-only connectivity scenarios with 100% accuracy across tested network configurations.
- **SC-003**: Each test result includes a clear pass/fail indicator and, on failure, a human-readable reason sufficient for a network engineer to identify the next diagnostic step without additional tools.
- **SC-004**: The diagnostic server is available on both IPv4 and IPv6 and responds to all protocol tests (HTTP, HTTPS, ICMP, DNS) with no single point of failure for either address family.
- **SC-005**: The exported test report contains all information needed to reproduce the test scenario (device network state, server endpoint, timestamps, per-test results) in a format readable without special software.
- **SC-006**: 464XLAT detection is accurate — the app correctly distinguishes between a native dual-stack device, a 464XLAT device, and an IPv4-only device in all tested cases.

## Assumptions

- The diagnostic server will be hosted in a network environment that supports both IPv4 and IPv6 public addressing (dual-stack hosting).
- The server component requires a domain name with both A and AAAA DNS records for DNS test validity; IP-only server configuration is not sufficient for DNS tests.
- The Android app targets devices running Android 8.0 (API 26) or later, as this covers the majority of active LTE-capable Android devices.
- ICMP/ICMPv6 tests from an Android app require root access or use of an underlying ping binary available on the device; the app will use available system methods and gracefully indicate if raw ICMP is unavailable.
- 464XLAT detection relies on identifying a CLAT network interface (typically named `clat` or `v4-` prefixed) or a synthesized RFC 1918/RFC 6052 address — this is standard Android 464XLAT behavior.
- The server TLS certificate for HTTPS tests will be from a publicly trusted CA so the Android app's default trust store accepts it without custom configuration.
- Tests bound to the cellular interface assume the device's mobile data is enabled; the app will detect and report if mobile data is off.
- The server-side component is self-hosted (not a cloud service subscription) and will be documented for deployment by the user or their team.
