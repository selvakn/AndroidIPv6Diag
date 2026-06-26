# Implementation Plan: 464XLAT Enhanced Diagnostics

**Branch**: `002-464xlat-diagnostics` | **Date**: 2026-06-26 | **Spec**: [spec.md](spec.md)

## Summary

Extends the existing Android IPv6 diagnostic app with five targeted 464XLAT sub-tests: NAT64 prefix discovery (RFC 7050), DNS64 synthesis validation, CLAT interface quality assessment, PLAT end-to-end path verification, and a consolidated 464XLAT results section with export support. No server changes are required. All decoding happens client-side.

## Technical Context

**Language/Version**: Kotlin 2.1 / Android API 26+
**Primary Dependencies**: OkHttp 4.12.0, Room 2.6.1, kotlinx.serialization 1.7.3, kotlinx.coroutines 1.9.0, android.net.DnsResolver (API 29+), Jetpack Compose + Material3
**Storage**: Room 2.6.1 — version bump 1→2, new `xlat_summaries` table
**Target Platform**: Android API 26+ on LTE/5G cellular
**Performance Goals**: All XLAT sub-tests complete in ≤15 seconds (SC-001); absence detected in ≤3 seconds (SC-005)
**Constraints**: Cellular binding via `ConnectivityManager.requestNetwork`, no root required, API 26+ fallbacks for `DnsResolver` and `getLinkMtu()`

## Project Structure

### Documentation (this feature)

```text
specs/002-464xlat-diagnostics/
├── plan.md              ← this file
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── xlat-export.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code Changes

```text
android/app/src/main/java/com/lenovo/mesh/ipv6diag/

data/model/
├── TestResult.kt          MODIFY — add NAT64_DISCOVERY, DNS64_VALIDATION,
│                                   CLAT_QUALITY, PLAT_VERIFICATION to TestType
└── XlatDiagnosticSummary.kt  NEW — all 5 XLAT model classes + enums

data/db/
├── AppDatabase.kt         MODIFY — bump to version 2, add migration, add XlatSummaryDao
├── Entities.kt            MODIFY — add XlatSummaryEntity
└── XlatSummaryDao.kt      NEW — insert / getBySession / deleteBySession

data/repository/
└── SessionRepository.kt   MODIFY — saveXlatSummary / getXlatSummary

diagnostic/
├── Nat64Discovery.kt      NEW — RFC 7050 prefix discovery
├── Dns64Validation.kt     NEW — DNS64 synthesis check
├── ClatQualityTest.kt     NEW — MTU read + latency comparison
├── PlatVerification.kt    NEW — HTTP to IPv4 server, decode PLAT IPv6 source
├── XlatRunner.kt          NEW — orchestrates the 4 sub-tests, returns XlatDiagnosticSummary
└── DiagnosticRunner.kt    MODIFY — add XLAT_464 to TestFilter, wire XlatRunner into
                                    executeTests(), save XlatDiagnosticSummary

export/
└── SessionExporter.kt     MODIFY — extend text + JSON exports with xlatSummary

ui/results/
└── ResultsScreen.kt       MODIFY — add XlatSummarySection composable
```

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Prefix discovery method | RFC 7050 `ipv4only.arpa` + well-known probe | Only standards-defined method; handles carrier-specific prefixes |
| Embedded IPv4 decoding | Client-side per RFC 6052 | No server changes; uses already-discovered prefix |
| XLAT data storage | New `xlat_summaries` Room table | Clean separation; no changes to existing tables |
| MTU measurement | `LinkProperties.getLinkMtu()` (API 29+) / `/sys/class/net/mtu` fallback | No root, no active probing |
| Filter integration | `XLAT_464` chip; auto-included in `ALL` when `clatPresent == true` | Consistent UX; zero extra UI for non-XLAT networks |
| Overall status | WORKING / PARTIAL / ABSENT / BROKEN | Actionable states for operators |

## RFC 6052 IPv4 Extraction

For the common /96 NAT64 prefix: the last 4 bytes of the 128-bit IPv6 address are the embedded IPv4 address.

```
ipv6 bytes: [0..11] = NAT64 prefix  [12..15] = IPv4
```

For other prefix lengths (supported for completeness):
- /32: IPv4 at bytes [4..7]
- /40: IPv4 at bytes [5..7, 9]
- /48: IPv4 at bytes [6..7, 9..10]
- /56: IPv4 at bytes [7, 9..11]
- /64: IPv4 at bytes [9..12]
- /96: IPv4 at bytes [12..15] (most common)
