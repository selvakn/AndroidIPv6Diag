# Quickstart: Add STUN and TURN Tests

## Scenario 1: Server supports STUN and TURN

1. Configure endpoint host with STUN/TURN service available.
2. Run diagnostics with default `ALL` filter.
3. Verify result list contains `STUN` and `TURN` entries.
4. Verify statuses are `PASS` (or TURN auth-required support evidence treated as supported).

## Scenario 2: Server does not support STUN/TURN

1. Configure endpoint host without STUN/TURN service.
2. Run diagnostics with default `ALL` filter.
3. Verify `STUN` and `TURN` entries are present.
4. Verify unsupported outcomes are `SKIPPED` with reason containing `server unsupported`.

## Scenario 3: Report persistence and visibility

1. Complete run containing STUN/TURN entries.
2. Open local results/history and confirm entries are displayed with status/reason.
3. Upload report and open dashboard detail.
4. Confirm STUN/TURN entries appear in test-results section.

## Smoke validation commands

```bash
# Server validation
cd server
go test ./...

# Android build verification
cd ../android
mise exec -- ./gradlew assembleDebug
```
