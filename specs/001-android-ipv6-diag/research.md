# Research: Android IPv6 Diagnostic Tool

**Phase**: 0 — Outline & Research  
**Branch**: `001-android-ipv6-diag`  
**Date**: 2026-06-26

---

## 1. Go Server

### Decision: Go Version
- **Chosen**: Go 1.24 (latest stable as of 2025)
- **Rationale**: LTS-quality release; `net/http` dual-stack improvements; supports `net/netip` for clean address-family detection.
- **Alternatives**: Go 1.22 (older), Go 1.23 — both superseded.

### Decision: Single Binary Build
- **Chosen**: `CGO_ENABLED=0 go build -ldflags="-s -w" -o server ./cmd/server`
- **Rationale**: CGO disabled produces a fully static binary with no shared-lib dependencies; runs on any Linux host without libc version concerns.
- **Alternatives**: CGO enabled (requires matching glibc on host), Docker multi-stage (adds container dependency).

### Decision: Dual-Stack HTTP Listening Strategy
- **Chosen**: Two separate `net.Listener` instances per port — one bound to `0.0.0.0:<port>` (IPv4), one to `[::1%<iface>]` — actually `[::]:port` for IPv6. Both share the same `http.Handler`. The handler extracts `RemoteAddr` and checks whether the client IP is an IPv4 or IPv6 address to populate the response's `address_family` field.
- **Rationale**: Separate listeners make address-family detection unambiguous at connection time. If a single dual-stack `[::]` listener is used, IPv4 clients appear as IPv4-mapped IPv6 addresses (e.g., `::ffff:1.2.3.4`), which requires parsing — error-prone.
- **Alternatives**: Single `[::]` listener with IPv4-mapped detection (fragile on some Linux configs with `net.ipv6only=1`).

### Decision: HTTPS / TLS
- **Chosen**: Standard library `crypto/tls` with `http.ServeTLS`. Certificate and key paths configurable via flags at startup.
- **Rationale**: No external TLS library needed; `crypto/tls` is production-grade.
- **Note**: Certificate must be from a publicly trusted CA (Let's Encrypt / ACME recommended). Self-signed certs will fail Android's default TrustManager.

### Decision: ICMP Handling
- **Chosen**: No Go code required. The OS responds to ICMP echo requests natively. The server just needs to be reachable and the host firewall must allow ICMP/ICMPv6 in.
- **Rationale**: Go cannot open raw ICMP sockets without elevated privileges; the OS ping responder is the correct layer.

### Decision: Diagnostic Response Payload
- **Chosen**: JSON response body on the HTTP/HTTPS diagnostic endpoint containing: `server_address`, `client_address`, `address_family` ("IPv4"|"IPv6"), `protocol` ("HTTP"|"HTTPS"), `timestamp` (RFC3339).
- **Rationale**: Lets the Android app verify end-to-end path correctness (FR-020) without parsing headers.

### Decision: Go Dependencies
- **Chosen**: Standard library only for the server core. No external HTTP framework needed.
- Potential optional add: `golang.org/x/net` (v0.38.0) only if `http2` tuning is needed.
- **Rationale**: Minimal dependency surface; single binary stays small and auditable.

### Decision: Makefile Targets
- **Chosen**:
  - `make build` — produces `bin/server` (Linux amd64 static binary)
  - `make build-arm64` — cross-compile for ARM64 (for ARM-based lab servers)
  - `make run` — build and run locally (HTTP only, no TLS cert needed for local dev)
  - `make clean` — remove `bin/`
  - `make test` — run Go unit tests
  - `make vet` — run `go vet`
- **Rationale**: Standard, self-documenting; no additional build tools beyond `make` and Go toolchain.

---

## 2. Android App

### Decision: Language & Toolchain
- **Chosen**: Kotlin 2.1.x with Kotlin Gradle DSL (`.kts` build files).
- **AGP (Android Gradle Plugin)**: 8.7.x
- **Gradle**: 8.11.x (via Gradle wrapper)
- **Rationale**: Kotlin 2.1 is the latest stable; AGP 8.7 supports SDK 35 targets and has first-class Compose support.
- **Alternatives**: Java (less idiomatic for modern Android), Kotlin 1.9 (older).

### Decision: UI Framework
- **Chosen**: Jetpack Compose (BOM 2025.04.01 or latest stable).
- **Rationale**: Native, modern; well-suited for a simple single-screen-per-function diagnostic app. No XML layouts needed; reduces boilerplate.
- **Alternatives**: View-based XML layouts (more verbose, less maintained).

### Decision: Minimum / Target SDK
- **Chosen**: `minSdk = 26` (Android 8.0, per spec assumption), `targetSdk = 35` (Android 15).
- **Rationale**: API 26 covers the vast majority of active LTE devices; Android 15 targeting enables latest security and permission features.

### Decision: Cellular Network Binding
- **Chosen**: `ConnectivityManager.requestNetwork()` with a `NetworkRequest` specifying `NetworkCapabilities.TRANSPORT_CELLULAR`. Once the callback delivers a `Network` object, all HTTP/DNS/socket tests are performed using that `Network` instance (via `network.bindSocket()` or `network.openConnection()`).
- **Rationale**: This is the only supported non-root way to force traffic over cellular when Wi-Fi is also active (FR-001).
- **Note**: The network callback approach is async; the app must hold the request active for the duration of the test session.

### Decision: HTTP / HTTPS Testing
- **Chosen**: OkHttp 4.12.0 with a custom `SocketFactory` derived from the bound cellular `Network` (`network.getSocketFactory()`). Two `OkHttpClient` instances: one targeting the server's IPv4 address, one targeting the IPv6 address.
- **Rationale**: OkHttp provides fine-grained control over socket creation, connection timeouts, and response parsing; it is the Android community standard.
- **Alternatives**: `HttpURLConnection` (lacks network binding API), Retrofit (over-engineered for direct IP tests without REST conventions).

### Decision: DNS Resolution Testing
- **Chosen**:
  - API 29+ (`DnsResolver`): Use `android.net.DnsResolver.query()` with the bound cellular `Network`, querying TYPE_A and TYPE_AAAA explicitly.
  - API 26–28 fallback: Bind a `DatagramSocket` to the cellular `Network` using `network.bindSocket()`, then use `InetAddress.getAllByName(hostname)` — this resolves via the network's system resolver.
  - DNS server IPs displayed using `connectivityManager.getLinkProperties(network)?.dnsServers` (API 21+).
- **Rationale**: `DnsResolver` (API 29+) allows querying specific record types explicitly and returns individual A/AAAA responses, which is needed to detect DNS64 synthesis accurately. The API 26-28 path is a graceful fallback; those devices show less granular results but still validate connectivity.

### Decision: ICMP Testing
- **Chosen**: `Runtime.exec(arrayOf("ping", "-c", "3", "-W", "5", ipv4Address))` and `Runtime.exec(arrayOf("ping6", "-c", "3", "-W", "5", ipv6Address))`. Parse stdout for RTT (min/avg/max) and packet loss percentage.
- **Rationale**: Raw ICMP sockets are not available to unprivileged Android apps. The `ping`/`ping6` binaries ship with Android and are available to non-root processes on all devices API 26+. Output is parseable and sufficient for diagnostic purposes.
- **Limitations**: If a device vendor removes `ping6`, the test degrades gracefully with a "ICMP unavailable on this device" result rather than a crash.

### Decision: Local Session Storage
- **Chosen**: Room 2.6.1 (SQLite ORM). Entities: `DiagnosticSessionEntity`, `TestResultEntity`. Relation: one session → many results.
- **Retention**: 50 sessions maximum; a cleanup job runs on each session save to delete the oldest when the count exceeds 50.
- **Rationale**: Room is the Android standard for structured local persistence; type-safe queries; coroutine-friendly.
- **Alternatives**: Plain JSON files (no query capability, harder to list/filter history), SharedPreferences (not suited for structured records).

### Decision: Export Format
- **Chosen**: Two export paths triggered from the share sheet:
  1. **Plain text**: Human-readable report with sections for Network Info, per-test results, and metadata.
  2. **JSON**: Serialized `DiagnosticSession` object (same fields as data model) via `kotlinx.serialization` (1.7.x).
- **Rationale**: Plain text is paste-ready for bug reports; JSON enables scripted comparison across devices or test runs.

### Decision: Dependency Versions (Android)
| Library | Version |
|---------|---------|
| Kotlin | 2.1.x |
| AGP | 8.7.x |
| Jetpack Compose BOM | 2025.04.01 |
| OkHttp | 4.12.0 |
| Room | 2.6.1 |
| kotlinx.serialization | 1.7.3 |
| kotlinx.coroutines | 1.9.0 |
| AndroidX Lifecycle | 2.8.x |
| AndroidX Navigation (Compose) | 2.8.x |

---

## 3. Shared / Infrastructure

### Decision: DNS Records for Server
- **Chosen**: The server hostname MUST have both an A record (IPv4) and AAAA record (IPv6) in a publicly or privately authoritative DNS zone.
- **Rationale**: Required by FR-019 to allow the Android app's DNS tests to verify both address families.
- **Not in code**: DNS record provisioning is a deployment step documented in `quickstart.md`.

### Decision: TLS Certificate Provisioning
- **Chosen**: Let's Encrypt via `certbot` or ACME DNS challenge. Certificate + key paths passed to server as `--cert` and `--key` flags.
- **Rationale**: Publicly trusted; free; automated renewal.
- **Alternative for private lab**: Use a custom CA and install the CA cert on test devices (document in quickstart).

### Decision: No Shared Code Between Server and App
- **Chosen**: Server (Go) and App (Android/Kotlin) are completely independent projects in separate directories. The only shared contract is the JSON response schema defined in `contracts/server-api.md`.
- **Rationale**: No shared runtime; different ecosystems. JSON contract is the natural boundary.
