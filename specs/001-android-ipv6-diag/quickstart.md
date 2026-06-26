# Quickstart: Android IPv6 Diagnostic Tool

**Date**: 2026-06-26

---

## Prerequisites

### Server Host Requirements
- Linux host (amd64 or arm64) with a public IPv4 address AND a public IPv6 address
- Domain name with DNS A record (IPv4) and AAAA record (IPv6) pointing to the host
- Ports 80 and 443 open inbound for TCP (HTTP/HTTPS)
- ICMP (type 8) and ICMPv6 (type 128) echo requests allowed inbound
- TLS certificate for the domain (Let's Encrypt recommended)

### Development Machine Requirements (Server)
- Go 1.24+ (`go version` to verify)
- `make`

### Development Machine Requirements (Android App)
- Android Studio Meerkat (2024.3+) or later
- Android SDK with API 35 installed
- JDK 17+
- A physical Android device with a SIM card (LTE) — the app tests cellular connectivity and is not meaningful on an emulator

---

## Part 1: Build and Deploy the Server

### 1. Build

```bash
cd server/
make build          # Produces bin/server (Linux amd64, static binary)
make build-arm64    # Cross-compile for ARM64
```

### 2. Configure TLS (production)

```bash
# Using certbot with Let's Encrypt (replace example.com with your domain)
sudo certbot certonly --standalone -d example.com
# Certificate: /etc/letsencrypt/live/example.com/fullchain.pem
# Key:         /etc/letsencrypt/live/example.com/privkey.pem
```

For a private lab with a custom CA, install the CA certificate on test Android devices via Settings → Security → Install from storage.

### 3. Run the server

```bash
# With TLS (production)
sudo ./bin/server \
  --cert /etc/letsencrypt/live/example.com/fullchain.pem \
  --key  /etc/letsencrypt/live/example.com/privkey.pem

# HTTP-only (local development, no TLS cert needed)
./bin/server
```

The server listens on `0.0.0.0:80`, `[::]:80`, `0.0.0.0:443`, and `[::]:443` by default.

### 4. Verify the server is reachable

```bash
# Health check
curl http://your-domain.com/health
curl http://[your-ipv6-addr]/health

# Diagnostic endpoint
curl http://your-domain.com/diag
curl https://your-domain.com/diag
curl http://[your-ipv6-addr]/diag
curl https://[your-ipv6-addr]/diag
```

Expected response from `/diag`:
```json
{
  "server_address": "203.0.113.1",
  "client_address": "198.51.100.5",
  "address_family": "IPv4",
  "protocol": "HTTP",
  "timestamp": "2026-06-26T10:30:00Z"
}
```

### 5. DNS validation

```bash
# Verify both A and AAAA records exist
dig A    your-domain.com
dig AAAA your-domain.com

# Verify ICMP (ping from a client machine)
ping  your-domain.com
ping6 your-domain.com
```

---

## Part 2: Build and Install the Android App

### 1. Open the project

Open `android/` in Android Studio.

### 2. Configure the default server

Edit `android/app/src/main/res/values/config.xml` (or the constants file noted in the project) and set:
```xml
<string name="default_server_hostname">your-domain.com</string>
```

Or configure it at runtime via the app's Settings screen.

### 3. Build and install

```bash
cd android/
./gradlew installDebug    # Build debug APK and install on connected device
```

Or use Android Studio's Run button with a connected physical device.

### 4. Run diagnostics

1. Enable mobile data on the test device (disable Wi-Fi to confirm cellular binding works)
2. Launch the app
3. Tap **Run All Tests**
4. Review results per protocol and address family
5. Check the **Network Info** screen to confirm IPv6 address assignment and CLAT status

---

## Part 3: Running Individual Tests

From the app's main screen:
- **All Tests** — full suite (HTTP, HTTPS, ICMP, DNS × IPv4 + IPv6 + XLAT if applicable)
- **DNS** — DNS A and AAAA lookups only
- **ICMP** — ping4 and ping6 only
- **HTTP / HTTPS** — TCP connectivity and server response only

---

## Part 4: Interpreting Results

| Result | Meaning |
|--------|---------|
| PASS | Test completed successfully; server confirmed correct address family |
| FAIL | Test failed; check the failure reason for next steps |
| SKIPPED | Test skipped (e.g., ICMP binary not available on this device) |
| ABORTED | Network changed during test run; re-run when network is stable |

### Common Failure Reasons

| Failure Reason | Likely Cause |
|---------------|--------------|
| No IPv6 address assigned | Carrier does not assign IPv6; check APN settings |
| ICMPv6 unreachable | Server firewall blocking ICMPv6; check security group rules |
| DNS AAAA returned IPv4-mapped | DNS64 synthesis detected — device is 464XLAT |
| HTTPS certificate error | Server cert not trusted; verify CA or install custom CA on device |
| Network changed during test | Device switched interfaces; keep LTE stable during test |
| CLAT detected, no IPv6 route | 464XLAT active but server unreachable via IPv6 prefix; check routing |

---

## Part 5: Exporting Results

After a test run:
1. Tap **Share Results**
2. Choose **Plain Text** (for bug reports / email) or **JSON** (for scripted analysis)
3. Use the Android share sheet to send via email, Slack, or save to files

---

## Makefile Reference (Server)

```
make build          Build static Linux amd64 binary → bin/server
make build-arm64    Cross-compile static Linux arm64 binary → bin/server-arm64
make run            Build and run locally (HTTP-only, no TLS)
make test           Run Go unit tests
make vet            Run go vet
make clean          Remove bin/ directory
```
