# Contract: STUN/TURN Result Integration

## Diagnostic Result Contract Extension

The existing `test_results` structure is extended by allowing `testType` values `STUN` and `TURN`.

### Result Entry Shape (existing)

```json
{
  "id": "uuid",
  "sessionId": "session-uuid",
  "testType": "STUN",
  "addressFamily": "IPv4",
  "status": "PASS",
  "latencyMs": 42,
  "failureReason": null,
  "resolvedAddress": "203.0.113.10",
  "iceCandidates": ["host 10.1.2.3:54321", "srflx 198.51.100.20:40000"],
  "serverConfirmedFamily": null,
  "packetLoss": null,
  "timestamp": 1751234567890
}
```

`iceCandidates` is optional and primarily populated for STUN test entries.

## Status Semantics for STUN/TURN

- `PASS`: Protocol-valid support evidence received (including TURN auth-challenge responses that prove service presence).
- `FAIL`: Probe reached endpoint but response is malformed/unexpected for protocol.
- `SKIPPED`: Capability unsupported or not available on target (`failureReason` must include `server unsupported`).
- `ABORTED`: Existing run-level abort semantics (e.g., network switched mid-run).

## Upload and Dashboard Compatibility

- `POST /reports` payload includes STUN/TURN entries in `test_results`.
- `GET /reports/{id}` returns same entries; dashboard renderer must display them without special-case filtering.

## Backward Compatibility

- Older reports without STUN/TURN remain valid.
- Consumers must not assume fixed test-type enumeration.
