# Quickstart: 464XLAT Enhanced Diagnostics

## Prerequisites
- Physical Android device on an LTE/5G network with 464XLAT (CLAT interface present)
- Diagnostic server running (from Feature 001) with a reachable IPv4 address
- App built and installed (`make android-build && adb install -r app-debug.apk`)

## Test Scenarios

### Scenario 1: Full 464XLAT working
Device on IPv6-only LTE with CLAT interface (`clat` or `v4-rmnet0`):
1. Open app → select `464XLAT` filter chip → tap Run
2. **Expected**: All 4 sub-tests PASS; overall status WORKING
3. **Verify**: NAT64 prefix shown (e.g. `64:ff9b::/96`); CLAT IPv4 shown; PLAT verified IPv6 source decoded to same IPv4

### Scenario 2: DNS64 misconfigured
Device has CLAT but DNS64 resolver is broken (or using a non-DNS64 resolver):
1. Open app → select `464XLAT` chip → tap Run
2. **Expected**: NAT64_DISCOVERY may PASS (well-known probe), DNS64_VALIDATION FAIL; overall PARTIAL

### Scenario 3: No 464XLAT (Wi-Fi or native dual-stack)
1. Open app → select `ALL` filter → tap Run
2. **Expected**: 464XLAT section shows "not detected"; XLAT sub-tests skipped; other tests unaffected

### Scenario 4: Export verification
After any completed session with 464XLAT:
1. Go to Results screen → tap Share JSON
2. **Verify**: JSON contains `xlatSummary` object with all five fields non-null

## Docker Compose test (limited)
The Docker test environment has no CLAT interface. Running with `ALL` or `464XLAT` filter:
- `nat64Prefix` → FAIL (no DNS64 in Docker)
- `dns64Validation` → FAIL (no synthesis)
- `clatQuality` → SKIPPED (no CLAT interface detected)
- `platVerification` → SKIPPED
- Overall: ABSENT (correct — no 464XLAT in Docker)
