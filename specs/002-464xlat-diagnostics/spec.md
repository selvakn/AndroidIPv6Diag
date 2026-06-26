# Feature Specification: 464XLAT Enhanced Diagnostics

**Feature Branch**: `002-464xlat-diagnostics`
**Created**: 2026-06-26
**Status**: Draft
**Input**: User description: "next feature is about adding diagnostic features for 464XLAT capabilities and nuances of it. explore and identify what can be added"

## Clarifications

### Session 2026-06-26

- Q: How are 464XLAT tests triggered in the existing app UI? → A: Add a `464XLAT` chip to the existing filter row; it runs all five sub-tests when selected; included in `ALL` automatically when CLAT is detected on the device.
- Q: Who decodes the embedded IPv4 from the PLAT-translated address? → A: The app decodes it client-side using the already-discovered NAT64 prefix. No server-side changes are needed; the existing `/diag` `client_address` field is sufficient.
- Q: When `ipv4only.arpa` returns multiple AAAA records (multiple NAT64 prefixes), which does the app use? → A: Record all discovered prefixes; prefer the IETF well-known `64:ff9b::/96` for DNS64 cross-checks if present, otherwise use the first; display the full list as a diagnostic detail.

## Background

464XLAT (RFC 6877) is the dominant IPv6 transition mechanism on modern LTE/5G networks. It consists of two components:

- **CLAT** (Customer-side Translator): Runs on the Android device. Creates a virtual interface that accepts IPv4 packets and translates them to IPv6 for transmission over the IPv6-only cellular link.
- **PLAT** (Provider-side Translator): Runs in the carrier network. Translates the IPv6 packets back to IPv4 before forwarding to IPv4-only destinations. Identified by a NAT64 prefix (e.g., `64:ff9b::/96` or a carrier-specific prefix).

The existing app detects CLAT interface presence, but does not validate whether 464XLAT is actually working end-to-end, measure its quality, or expose the rich diagnostic data that operators and developers need.

## User Scenarios & Testing

### User Story 1 — NAT64 Prefix Discovery (Priority: P1)

A network engineer testing a carrier's 464XLAT deployment needs to confirm which NAT64 prefix the network uses and whether the device can auto-discover it. Without knowing the NAT64 prefix, no further 464XLAT analysis is possible.

**Why this priority**: NAT64 prefix discovery is the foundation of all other 464XLAT diagnostics. All downstream tests depend on knowing the prefix.

**Independent Test**: Can be tested on any IPv6-only or dual-stack cellular network by running prefix discovery alone and observing whether the prefix is found, what it is, and which discovery method succeeded.

**Acceptance Scenarios**:

1. **Given** the device is on a cellular network with 464XLAT, **When** the user runs discovery, **Then** the app displays the NAT64 prefix (e.g., `64:ff9b::/96` or carrier-specific), the discovery method used (well-known prefix probe or `ipv4only.arpa` DNS query per RFC 7050), and the result timestamp.
2. **Given** the device is on a network without NAT64, **When** the user runs discovery, **Then** the app clearly indicates "NAT64 prefix not found" and explains implications for 464XLAT.
3. **Given** the carrier uses a non-standard prefix, **When** discovery runs, **Then** the app identifies the carrier-specific prefix via the `ipv4only.arpa` DNS synthesis method.

---

### User Story 2 — DNS64 Synthesis Validation (Priority: P2)

A developer whose app does direct DNS lookups needs to verify that the carrier's DNS64 resolver correctly synthesises AAAA records for IPv4-only destinations, and that the synthesised address encodes the correct NAT64 prefix.

**Why this priority**: DNS64 is the mechanism that makes IPv4-only hostnames reachable on IPv6-only networks. Misconfigured DNS64 is a common cause of app failures on 464XLAT networks.

**Independent Test**: Can be tested independently by querying a known IPv4-only hostname and verifying the synthesised AAAA record against the discovered NAT64 prefix.

**Acceptance Scenarios**:

1. **Given** DNS64 is functioning, **When** the app queries a known IPv4-only hostname, **Then** it displays the synthesised AAAA record, decodes the embedded IPv4 address from the NAT64 prefix, and confirms they match.
2. **Given** DNS64 is misconfigured, **When** the app queries an IPv4-only hostname, **Then** it reports the failure mode — whether no AAAA record is returned, the prefix is wrong, or the embedded IPv4 is incorrect.
3. **Given** a dual-stack hostname, **When** queried, **Then** the app distinguishes between a native AAAA record and a synthesised one, and labels each accordingly.

---

### User Story 3 — CLAT Interface Quality Assessment (Priority: P2)

A network operator investigating user-reported slowness on LTE needs to measure the overhead and quality of the CLAT translation path compared to native IPv6, to determine whether 464XLAT is introducing meaningful latency or MTU fragmentation.

**Why this priority**: CLAT adds per-packet overhead. MTU reduction (20-byte IPv6 header added to IPv4 traffic) and translation latency directly affect app performance. This is P2 because CLAT detection already exists; this extends it with quality measurement.

**Independent Test**: Can be tested independently by comparing RTT and MTU results on the CLAT interface against native IPv6. Delivers actionable data even without other 464XLAT tests.

**Acceptance Scenarios**:

1. **Given** a CLAT interface is present, **When** the quality assessment runs, **Then** the app displays CLAT interface MTU, effective IPv4 payload MTU (reduced by translation overhead), and whether path MTU discovery is likely to cause issues.
2. **Given** both CLAT and native IPv6 paths are available, **When** assessed, **Then** the app shows side-by-side latency comparison (CLAT path vs. native IPv6 path to the same server) with the overhead in milliseconds.
3. **Given** CLAT is present but its interface is degraded (e.g., no valid address), **When** assessed, **Then** the app reports the specific configuration fault.

---

### User Story 4 — End-to-End 464XLAT Path Verification (Priority: P3)

A QA engineer validating 464XLAT on a new device model needs proof that traffic actually traverses the full CLAT→PLAT→IPv4 path and that the PLAT correctly encodes the original IPv4 client address in the IPv6 source, so that server-side logging and geo-IP work correctly.

**Why this priority**: Previous stories validate the components; this validates the whole chain. It requires the diagnostic server to be deployed and reachable.

**Independent Test**: Can be tested independently against the companion diagnostic server by running an IPv4 connectivity test over the cellular interface and examining what client address the server reports.

**Acceptance Scenarios**:

1. **Given** the device is on a 464XLAT network, **When** the app sends an HTTP request to the server's IPv4 address via the CLAT path, **Then** the server response confirms it received an IPv6 source address (the PLAT-translated address), and the app decodes the embedded original IPv4 from that address and displays it.
2. **Given** the PLAT address is from the discovered NAT64 prefix, **When** displayed, **Then** the app confirms the PLAT address is consistent with the prefix and highlights the embedded IPv4 component.
3. **Given** 464XLAT is broken (PLAT unreachable), **When** the test runs, **Then** the app distinguishes between "CLAT present but PLAT unreachable" and "CLAT absent", giving specific failure messages.

---

### User Story 5 — Comprehensive 464XLAT Diagnostic Report (Priority: P3)

A network engineer preparing a report for a carrier needs all 464XLAT findings in one consolidated view that can be exported, covering prefix discovery, DNS64 health, CLAT quality, and path verification with a pass/fail summary for each component.

**Why this priority**: Builds on all prior stories; the report is only meaningful once the individual components are reliable.

**Independent Test**: Can be tested by running all 464XLAT sub-tests sequentially and verifying that the exported report contains a correctly structured summary with results for each component.

**Acceptance Scenarios**:

1. **Given** all 464XLAT sub-tests are complete, **When** the user views the report, **Then** a dedicated 464XLAT section shows NAT64 prefix, DNS64 status, CLAT quality metrics, and path verification result in a single scrollable view.
2. **Given** the user taps "Export", **When** the report is exported (text or JSON), **Then** it includes all 464XLAT fields in the structured output alongside existing test results.
3. **Given** 464XLAT is not present, **When** the report is viewed, **Then** the section clearly states "464XLAT not detected on this network" rather than leaving it blank.

---

### Edge Cases

- What happens when the device has CLAT but no IPv6 connectivity (degenerate state)?
- How does the app behave when the NAT64 well-known prefix (`64:ff9b::/96`) and the carrier-specific prefix both respond?
- What if DNS64 synthesises addresses but the PLAT is unreachable (DNS works, data plane broken)?
- How does the app handle a CLAT interface that exists but has no assigned address?
- When `ipv4only.arpa` DNS query returns multiple AAAA records (multiple NAT64 prefixes): all are recorded and displayed; the well-known `64:ff9b::/96` is preferred for cross-checks; multiple carrier-specific prefixes are shown as a diagnostic detail (may indicate a misconfigured network).
- What happens when the device switches between a 464XLAT network and a native dual-stack network mid-test?

## Requirements

### Functional Requirements

- **FR-001**: The app MUST attempt NAT64 prefix discovery using both the well-known prefix probe (`64:ff9b::/96`) and the `ipv4only.arpa` RFC 7050 DNS method, recording all discovered prefixes. The IETF well-known prefix (`64:ff9b::/96`) is preferred for cross-checks when present; otherwise the first discovered prefix is used.
- **FR-002**: The app MUST display all discovered NAT64 prefixes in full CIDR notation with the preferred prefix highlighted, or clearly indicate none were found.
- **FR-003**: The app MUST query a known IPv4-only hostname via the cellular interface's DNS resolver and display the returned AAAA record along with the decoded embedded IPv4 address.
- **FR-004**: The app MUST validate that the synthesised AAAA record's prefix matches the discovered NAT64 prefix, and flag a mismatch as a configuration warning.
- **FR-005**: The app MUST report the CLAT interface MTU and the effective IPv4 payload MTU (accounting for 464XLAT overhead).
- **FR-006**: When both CLAT and native IPv6 paths are available, the app MUST display side-by-side round-trip latency to the same destination for each path.
- **FR-007**: The app MUST send an HTTP request to the diagnostic server's IPv4 address over the cellular interface and display the client IPv6 address that the server observed (the PLAT-translated address) from the existing `client_address` field in the `/diag` response.
- **FR-008**: The app MUST decode the embedded IPv4 component from the PLAT-translated IPv6 source address entirely client-side, using the NAT64 prefix discovered in FR-001, and confirm it matches the device's CLAT IPv4 address. No server-side changes are required.
- **FR-009**: The app MUST add a `464XLAT` filter chip to the existing test filter row. When selected, it runs all five 464XLAT sub-tests. When `ALL` is selected and CLAT is detected, 464XLAT sub-tests are included automatically. Results appear in a dedicated 464XLAT section in the results view with a summary pass/fail for the chain.
- **FR-010**: All 464XLAT diagnostic data MUST be included in both the plain-text and JSON export formats.
- **FR-011**: The app MUST run all 464XLAT sub-tests bound to the cellular network interface, not via Wi-Fi or default routing.
- **FR-012**: If no CLAT interface is detected, the app MUST skip 464XLAT sub-tests and display a clear "464XLAT not present on this network" message.

### Key Entities

- **NAT64PrefixResult**: Represents all discovered NAT64 prefixes — a list of entries each containing the prefix in CIDR notation, discovery method (well-known probe / RFC 7050 DNS), and whether it is the IETF well-known prefix or carrier-specific. Includes a `preferredPrefix` field pointing to the well-known prefix when present, otherwise the first entry.
- **DNS64ValidationResult**: Represents the outcome of querying an IPv4-only hostname — the queried hostname, raw AAAA records returned, decoded embedded IPv4, whether synthesis was detected, and whether the prefix matches the NAT64 prefix result.
- **ClatQualityResult**: Represents CLAT interface quality metrics — interface name, CLAT IPv4 address, interface MTU, effective payload MTU, latency via CLAT path, latency via native IPv6 path (if available), and latency delta.
- **PlatVerificationResult**: Represents the end-to-end path verification — whether the server observed an IPv6 source (confirming PLAT translation), the PLAT IPv6 address, the decoded embedded IPv4, and whether it matches the CLAT address.
- **XlatDiagnosticSummary**: Top-level 464XLAT result containing all sub-results and an overall chain status (WORKING / PARTIAL / ABSENT / BROKEN).

## Success Criteria

### Measurable Outcomes

- **SC-001**: On a confirmed 464XLAT network, all four sub-tests (prefix discovery, DNS64 validation, CLAT quality, path verification) complete within 15 seconds total.
- **SC-002**: NAT64 prefix discovery correctly identifies the active prefix on 100% of networks where CLAT is present and `ipv4only.arpa` is supported by the carrier's DNS.
- **SC-003**: The decoded embedded IPv4 in the PLAT-translated address matches the device's CLAT IPv4 address in all cases where 464XLAT is functioning correctly.
- **SC-004**: The exported JSON report contains all five 464XLAT entities with no missing fields when tests complete, enabling offline analysis by network engineers.
- **SC-005**: On networks without 464XLAT, the app correctly identifies absence and skips 464XLAT tests in under 3 seconds.
- **SC-006**: The side-by-side CLAT vs. native IPv6 latency comparison is accurate to within ±5ms, sufficient for meaningful path comparison.

## Assumptions

- No server-side changes are required for FR-007/FR-008. The app decodes the PLAT-translated IPv6 address client-side using the NAT64 prefix discovered in US1. The existing `/diag` `client_address` field is sufficient.
- The `ipv4only.arpa` DNS query method (RFC 7050) is used for NAT64 prefix discovery; DNS query is made via the cellular network's resolver, not a hardcoded public resolver.
- The well-known NAT64 probe hostname used for DNS64 validation is `ipv4only.arpa` or a similarly guaranteed IPv4-only name; the exact probe hostname can be configurable in app settings.
- A 464XLAT network is one where a CLAT interface is present on the device; the absence of CLAT is treated as "464XLAT not active", even if PLAT may exist in the network.
- MTU measurement uses the CLAT interface's reported MTU from the OS, not active probe packets; active MTU probing is out of scope.
- Server-side changes are minimal: the `/diag` endpoint already returns `client_address`; the server only needs to ensure it returns the full IPv6 address (including PLAT-translated ones) without truncation.
