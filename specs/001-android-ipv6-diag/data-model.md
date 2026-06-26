# Data Model: Android IPv6 Diagnostic Tool

**Phase**: 1 — Design  
**Date**: 2026-06-26

---

## Entities

### DiagnosticSession

Represents a single invocation of a test run (full suite or individual protocol).

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Unique session identifier |
| `timestamp` | Instant (epoch ms) | NOT NULL | When the session started |
| `serverEndpoint` | FK → ServerEndpoint | NOT NULL | Server used for this session |
| `networkInfo` | Embedded | NOT NULL | Snapshot of device network state at session start |
| `testResults` | List\<TestResult\> | 1-to-many | All test results for this session |
| `status` | Enum: RUNNING / COMPLETED / ABORTED | NOT NULL | Session lifecycle state |
| `abortReason` | String? | Nullable | Populated if status = ABORTED (e.g., "network changed during test") |

**Retention**: Maximum 50 sessions stored locally. When a new session is saved and the count exceeds 50, the oldest session (by timestamp) is deleted.

**State transitions**:
```
RUNNING → COMPLETED  (all tests finished, even if some failed)
RUNNING → ABORTED    (network change detected mid-run)
```

---

### TestResult

Represents the outcome of a single protocol test for one address family.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | UUID | PK, auto-generated | Unique result identifier |
| `sessionId` | UUID | FK → DiagnosticSession | Parent session |
| `testType` | Enum: HTTP / HTTPS / ICMP / DNS | NOT NULL | Protocol under test |
| `addressFamily` | Enum: IPv4 / IPv6 / XLAT | NOT NULL | Which address path was tested |
| `status` | Enum: PASS / FAIL / SKIPPED / ABORTED | NOT NULL | Outcome of this test |
| `latencyMs` | Long? | Nullable | Round-trip time in milliseconds; null if not measurable |
| `failureReason` | String? | Nullable | Human-readable reason if status ≠ PASS |
| `resolvedAddress` | String? | Nullable | For DNS tests: the address resolved; for HTTP/ICMP: the target IP |
| `serverConfirmedFamily` | String? | Nullable | Address family as reported by server response payload (HTTP/HTTPS only) |
| `packetLoss` | Float? | Nullable | 0.0–1.0; ICMP tests only |
| `timestamp` | Instant (epoch ms) | NOT NULL | When this individual test ran |

**XLAT address family**: Used when the test path goes through 464XLAT (CLAT interface). A test can be IPv4 on the wire but carried over an IPv6-only RAN via CLAT — this is explicitly tagged.

---

### NetworkInfo

Snapshot of the device's network state captured at session start. Stored embedded inside DiagnosticSession (not a separate table).

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `cellularInterfaceName` | String? | Nullable | e.g., `rmnet_data0`, `ccmni0` |
| `cellularIPv4Address` | String? | Nullable | IPv4 address on cellular interface |
| `cellularIPv6Addresses` | List\<String\> | May be empty | All global unicast IPv6 addresses on cellular |
| `hasNativeIPv6` | Boolean | NOT NULL | True if at least one global unicast IPv6 address is present |
| `clatPresent` | Boolean | NOT NULL | True if a CLAT interface (`clat` or `v4-*`) is detected |
| `clatInterfaceName` | String? | Nullable | CLAT interface name if present |
| `clatSyntheticIPv4` | String? | Nullable | The RFC 1918 IPv4 address synthesized by CLAT |
| `clatIPv6Prefix` | String? | Nullable | The NAT64/DNS64 prefix used (e.g., `64:ff9b::/96`) |
| `dnsServers` | List\<String\> | May be empty | IP addresses of DNS resolvers assigned to cellular interface |
| `dnsServerNames` | List\<String\> | May be empty | Reverse-resolved hostnames of DNS servers (best-effort) |
| `mobileDataEnabled` | Boolean | NOT NULL | False if mobile data is disabled system-wide |
| `apiLevel` | Int | NOT NULL | Android API level of the device (affects DNS test method used) |

---

### ServerEndpoint

The diagnostic server configuration. One default endpoint; user may override.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | UUID | PK | Unique identifier |
| `hostname` | String | NOT NULL | Fully qualified domain name (required for DNS tests) |
| `ipv4Address` | String? | Nullable | Explicit IPv4 address (resolved or manually configured) |
| `ipv6Address` | String? | Nullable | Explicit IPv6 address (resolved or manually configured) |
| `httpPort` | Int | NOT NULL, default 80 | Port for HTTP tests |
| `httpsPort` | Int | NOT NULL, default 443 | Port for HTTPS tests |
| `isDefault` | Boolean | NOT NULL | True for the built-in default server |
| `lastVerified` | Instant? | Nullable | When this endpoint was last successfully reached |

**Constraint**: At most one `ServerEndpoint` record has `isDefault = true` at any time.

---

## Relationships

```
ServerEndpoint ─────────────────── DiagnosticSession
     1                                    *

DiagnosticSession ──────────────── TestResult
       1                                  *

DiagnosticSession ──────────────── NetworkInfo
       1                                  1  (embedded)
```

---

## Local Storage Schema (Room / SQLite)

### Tables

**`diagnostic_sessions`**
```
id               TEXT PRIMARY KEY
timestamp        INTEGER NOT NULL
server_id        TEXT NOT NULL REFERENCES server_endpoints(id)
network_info     TEXT NOT NULL  -- JSON blob (NetworkInfo serialized)
status           TEXT NOT NULL  -- RUNNING | COMPLETED | ABORTED
abort_reason     TEXT
```

**`test_results`**
```
id                      TEXT PRIMARY KEY
session_id              TEXT NOT NULL REFERENCES diagnostic_sessions(id)
test_type               TEXT NOT NULL  -- HTTP | HTTPS | ICMP | DNS
address_family          TEXT NOT NULL  -- IPv4 | IPv6 | XLAT
status                  TEXT NOT NULL  -- PASS | FAIL | SKIPPED | ABORTED
latency_ms              INTEGER
failure_reason          TEXT
resolved_address        TEXT
server_confirmed_family TEXT
packet_loss             REAL
timestamp               INTEGER NOT NULL
```

**`server_endpoints`**
```
id             TEXT PRIMARY KEY
hostname       TEXT NOT NULL
ipv4_address   TEXT
ipv6_address   TEXT
http_port      INTEGER NOT NULL DEFAULT 80
https_port     INTEGER NOT NULL DEFAULT 443
is_default     INTEGER NOT NULL DEFAULT 0  -- SQLite boolean
last_verified  INTEGER
```

### Indexes

- `test_results(session_id)` — fast lookup of results for a session
- `diagnostic_sessions(timestamp DESC)` — history list ordered by newest first

---

## Server-Side Data (stateless)

The Go server is stateless — it stores no session data. Each HTTP/HTTPS response returns a transient JSON payload; the Android app owns all persistence.
