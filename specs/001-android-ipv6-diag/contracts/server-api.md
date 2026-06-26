# Server API Contract: IPv6 Diagnostic Server

**Component**: Go diagnostic server  
**Date**: 2026-06-26  
**Consumer**: Android diagnostic app

---

## Overview

The server exposes a single diagnostic HTTP/HTTPS endpoint. Its only purpose is to receive a connection from the Android app and return a JSON payload confirming which address family and protocol the connection arrived on. The server is stateless — no authentication, no sessions.

ICMP and DNS are handled at the OS/infrastructure level (not HTTP endpoints).

---

## Endpoints

### `GET /diag`

Returns connection metadata for the active request. The Android app makes this request twice per test run — once to the server's IPv4 address and once to the IPv6 address — on both port 80 (HTTP) and port 443 (HTTPS).

**Request**

```
GET /diag HTTP/1.1
Host: <server-hostname>
```

No request body, no query parameters, no authentication headers required.

**Response — 200 OK**

```json
{
  "server_address": "2001:db8::1",
  "client_address": "2001:db8:feed::cafe",
  "address_family": "IPv6",
  "protocol": "HTTPS",
  "timestamp": "2026-06-26T10:30:00Z"
}
```

| Field | Type | Values | Description |
|-------|------|--------|-------------|
| `server_address` | string | IPv4 or IPv6 literal | The server's local address this connection arrived on |
| `client_address` | string | IPv4 or IPv6 literal | The remote address of the Android device |
| `address_family` | string | `"IPv4"` or `"IPv6"` | Determined from `server_address` — IPv4-mapped addresses (`::ffff:x.x.x.x`) are classified as `"IPv4"` |
| `protocol` | string | `"HTTP"` or `"HTTPS"` | Determined by which listener received the connection |
| `timestamp` | string | RFC3339 UTC | Server-side time the request was processed |

**Response — 400 Bad Request**

Returned only if the request method is not GET.

```json
{
  "error": "method not allowed"
}
```

**Response — 500 Internal Server Error**

Returned if the server cannot determine its local address.

```json
{
  "error": "internal error",
  "detail": "<error message>"
}
```

---

### `GET /health`

Liveness check. Returns 200 with a plain text `ok` body. Used by monitoring and by the Android app to quickly confirm the server is reachable before running the full test suite.

**Response — 200 OK**

```
Content-Type: text/plain

ok
```

---

## Listener Configuration

The server MUST start four listeners:

| Listener | Bind Address | Port | Protocol |
|----------|-------------|------|----------|
| IPv4 HTTP | `0.0.0.0` | 80 | HTTP |
| IPv4 HTTPS | `0.0.0.0` | 443 | HTTPS (TLS) |
| IPv6 HTTP | `[::]` | 80 | HTTP |
| IPv6 HTTPS | `[::]` | 443 | HTTPS (TLS) |

Each listener is a separate `net.Listener`. The HTTP and HTTPS listeners on IPv4 and IPv6 share the same handler (`/diag`, `/health`). The `protocol` field in the response is determined by which listener received the connection, not by request headers.

**IPv4/IPv6 separation note**: `[::]` on Linux with `net.ipv6only = 1` (or Go's default behaviour on most platforms) does NOT accept IPv4 connections. Separate IPv4 (`0.0.0.0`) and IPv6 (`[::]`) listeners ensure clean address-family separation without relying on IPv4-mapped IPv6 addresses.

---

## ICMP Contract

No HTTP endpoint. The server host must:
- Allow inbound ICMPv4 echo requests (type 8) and reply with ICMPv4 echo replies (type 0)
- Allow inbound ICMPv6 echo requests (type 128) and reply with ICMPv6 echo replies (type 129)
- Firewall / security group rules must explicitly permit these ICMP types

---

## DNS Contract

No HTTP endpoint. The server's hostname MUST have:
- An **A record** resolving to the server's public IPv4 address
- A **AAAA record** resolving to the server's public IPv6 address

The Android app queries these records using the system (carrier-assigned) DNS resolver to validate both address families and to detect DNS64 behaviour on 464XLAT networks.

---

## Command-Line Interface (Server Binary)

```
server [flags]

Flags:
  --http-addr   string   IPv4 HTTP listen address (default "0.0.0.0:80")
  --http6-addr  string   IPv6 HTTP listen address (default "[::]:80")
  --https-addr  string   IPv4 HTTPS listen address (default "0.0.0.0:443")
  --https6-addr string   IPv6 HTTPS listen address (default "[::]:443")
  --cert        string   Path to TLS certificate file (PEM)
  --key         string   Path to TLS private key file (PEM)
  --version              Print version and exit
```

If `--cert` and `--key` are not provided, HTTPS listeners are disabled and the server runs HTTP-only (useful for local development).

---

## Versioning

The API is unversioned. The `/diag` response schema is stable; additive fields may be added in future without a version bump. Removing or renaming fields requires a coordinated app update.
