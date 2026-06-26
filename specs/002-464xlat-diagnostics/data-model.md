# Data Model: 464XLAT Enhanced Diagnostics

## New Kotlin Model Classes (`data/model/XlatDiagnosticSummary.kt`)

### Nat64PrefixEntry
Single discovered NAT64 prefix:
- `prefix: String` — CIDR notation, e.g. "64:ff9b::/96"
- `prefixLengthBits: Int` — e.g. 96
- `discoveryMethod: Nat64DiscoveryMethod` — enum: WELL_KNOWN_PROBE, RFC7050_DNS
- `isWellKnown: Boolean` — true when prefix == "64:ff9b::/96"

### NAT64PrefixResult
- `entries: List<Nat64PrefixEntry>` — all discovered prefixes (may be empty)
- `preferredPrefix: String?` — well-known if present, else first entry, else null
- `status: XlatSubTestStatus` — PASS / FAIL / SKIPPED
- `failureReason: String?`

### DNS64ValidationResult
- `queriedHostname: String` — always "ipv4only.arpa"
- `rawAAAARecords: List<String>` — raw synthesised AAAA addresses returned
- `decodedEmbeddedIPv4: String?` — IPv4 decoded from synthesised AAAA
- `synthesisTested: Boolean` — true if AAAA was returned for an IPv4-only name
- `prefixMatches: Boolean` — decoded prefix matches NAT64PrefixResult.preferredPrefix
- `status: XlatSubTestStatus`
- `failureReason: String?`

### ClatQualityResult
- `interfaceName: String` — e.g. "clat" or "v4-rmnet0"
- `clatIPv4Address: String?` — IPv4 address on CLAT interface
- `interfaceMtu: Int?` — CLAT interface MTU
- `effectiveIPv4Mtu: Int?` — interfaceMtu − 20
- `clatLatencyMs: Long?` — RTT via CLAT path (ICMP to server IPv4 address)
- `nativeIPv6LatencyMs: Long?` — RTT via native IPv6 path (ICMP to server IPv6 address)
- `latencyDeltaMs: Long?` — clatLatencyMs − nativeIPv6LatencyMs (null if either unavailable)
- `status: XlatSubTestStatus`
- `failureReason: String?`

### PlatVerificationResult
- `serverObservedIPv6Source: String?` — full IPv6 address the server saw as client_address
- `decodedEmbeddedIPv4: String?` — decoded from serverObservedIPv6Source using preferredPrefix
- `matchesClatIPv4: Boolean` — decodedEmbeddedIPv4 == ClatQualityResult.clatIPv4Address
- `platIPv6Prefix: String?` — prefix portion of serverObservedIPv6Source
- `prefixMatchesDiscovered: Boolean` — platIPv6Prefix matches NAT64PrefixResult.preferredPrefix
- `status: XlatSubTestStatus`
- `failureReason: String?`

### XlatDiagnosticSummary (top-level)
- `sessionId: String`
- `nat64Prefix: NAT64PrefixResult`
- `dns64Validation: DNS64ValidationResult`
- `clatQuality: ClatQualityResult`
- `platVerification: PlatVerificationResult`
- `overallStatus: XlatChainStatus` — WORKING / PARTIAL / ABSENT / BROKEN
- `timestamp: Long`

### Enums
- `Nat64DiscoveryMethod` — WELL_KNOWN_PROBE, RFC7050_DNS
- `XlatSubTestStatus` — PASS, FAIL, SKIPPED
- `XlatChainStatus` — WORKING, PARTIAL, ABSENT, BROKEN

**Overall status logic**:
- ABSENT: no CLAT interface detected
- WORKING: NAT64 found + DNS64 pass + PLAT verification pass
- PARTIAL: NAT64 found but DNS64 or PLAT verification failed
- BROKEN: CLAT present but NAT64 not found

## Extended Enums (existing files)

### TestType (existing `data/model/TestResult.kt`)
Add new values: `NAT64_DISCOVERY`, `DNS64_VALIDATION`, `CLAT_QUALITY`, `PLAT_VERIFICATION`

These map XLAT sub-tests to the existing `TestResult` rows stored per-session, enabling history view integration. Rich XLAT data is stored separately in `xlat_summaries`.

## Room Changes

### New Entity: `XlatSummaryEntity` (in `data/db/Entities.kt`)
```
Table: xlat_summaries
Columns:
  session_id  TEXT  PRIMARY KEY  FK → diagnostic_sessions(id) CASCADE DELETE
  summary_json TEXT NOT NULL     Full XlatDiagnosticSummary serialised as JSON
  overall_status TEXT NOT NULL   XlatChainStatus.name for quick filtering
```

### AppDatabase version: 1 → 2
Migration adds the `xlat_summaries` table. No changes to existing tables.

### New DAO: `XlatSummaryDao`
- `insert(entity: XlatSummaryEntity)`
- `getBySession(sessionId: String): XlatSummaryEntity?`
- `deleteBySession(sessionId: String)`
