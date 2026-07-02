# Quickstart: Browser-Based Network Test Suite

## Prerequisites
- Go toolchain installed (project uses Go modules).
- Running server binary from `server/`.
- Browser with WebRTC support.

## 1) Start server

```bash
cd server
go run ./cmd/server \
  -http-addr 0.0.0.0:8080 \
  -http6-addr [::]:8080
```

Optional TURN-related environment:

```bash
export TURN_ENABLED=true
export TURN_CREDENTIALS_TOKEN=""
```

## 2) Open diagnostics page
- Navigate to `http://<server-host>:8080/browser-diagnostics`.
- Confirm defaults are loaded.
- Confirm config endpoint returns JSON at `http://<server-host>:8080/browser-diagnostics/config`.

### Optional target overrides

```bash
export BROWSER_DIAG_HTTP_TARGET="http://<server-host>:8080/diag"
export BROWSER_DIAG_HTTPS_TARGET="https://example.com"
export BROWSER_DIAG_STUN_TARGET="stun:stun.l.google.com:19302"
```

## 3) Run baseline suite (US1)
- Keep default targets selected.
- Start all five tests.
- Verify each test reaches terminal status (`passed|failed|timed_out|unsupported`).

## 4) Validate mixed target mode (US2)
- Add at least one custom target.
- Re-run selected tests.
- Confirm results label target origin (`default` vs `custom`).

## 5) Validate repeated run comparison (US3)
- Execute two runs with different network conditions (or target availability).
- Confirm run history entries are distinct and show timestamps/outcomes.

## 6) Negative path checks
- Start run with no selected tests and verify user-facing validation message.
- Use intentionally unreachable targets and verify clear failure reasons.
- Force timeout by pointing to a non-responsive endpoint and verify run continues.
