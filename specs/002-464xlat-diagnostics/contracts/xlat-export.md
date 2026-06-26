# Contract: 464XLAT Export Format

## No server changes required
The existing `/diag` endpoint already returns `client_address` as a full string.
The app decodes NAT64 addresses client-side. No server API changes.

## JSON Export Extension

The existing `DiagnosticSession` JSON export gains an optional `xlatSummary` field:

```json
{
  "id": "...",
  "timestamp": 1234567890,
  "serverEndpoint": { ... },
  "networkInfo": { ... },
  "testResults": [ ... ],
  "xlatSummary": {
    "sessionId": "...",
    "overallStatus": "WORKING",
    "nat64Prefix": {
      "entries": [
        {
          "prefix": "64:ff9b::/96",
          "prefixLengthBits": 96,
          "discoveryMethod": "RFC7050_DNS",
          "isWellKnown": true
        }
      ],
      "preferredPrefix": "64:ff9b::/96",
      "status": "PASS",
      "failureReason": null
    },
    "dns64Validation": {
      "queriedHostname": "ipv4only.arpa",
      "rawAAAARecords": ["64:ff9b::c000:00aa"],
      "decodedEmbeddedIPv4": "192.0.0.170",
      "synthesisTested": true,
      "prefixMatches": true,
      "status": "PASS",
      "failureReason": null
    },
    "clatQuality": {
      "interfaceName": "clat",
      "clatIPv4Address": "192.0.0.4",
      "interfaceMtu": 1480,
      "effectiveIPv4Mtu": 1460,
      "clatLatencyMs": 45,
      "nativeIPv6LatencyMs": 32,
      "latencyDeltaMs": 13,
      "status": "PASS",
      "failureReason": null
    },
    "platVerification": {
      "serverObservedIPv6Source": "64:ff9b::c000:0004",
      "decodedEmbeddedIPv4": "192.0.0.4",
      "matchesClatIPv4": true,
      "platIPv6Prefix": "64:ff9b::",
      "prefixMatchesDiscovered": true,
      "status": "PASS",
      "failureReason": null
    },
    "timestamp": 1234567890
  }
}
```

`xlatSummary` is `null` when CLAT was not detected or 464XLAT filter was not selected.

## Plain-Text Export Extension

After the existing `--- Test Results ---` section, add:

```
--- 464XLAT Diagnostics ---
Overall chain  : WORKING
NAT64 prefix   : 64:ff9b::/96  [well-known, RFC7050_DNS]
DNS64 synthesis: PASS  decoded=192.0.0.170  prefix-match=true
CLAT interface : clat  mtu=1480  effective-ipv4-mtu=1460
CLAT latency   : 45ms  native-ipv6=32ms  delta=+13ms
PLAT verified  : server-saw=64:ff9b::c000:0004  decoded-ipv4=192.0.0.4  clat-match=true
----------------------------
```

When 464XLAT is absent:
```
--- 464XLAT Diagnostics ---
464XLAT not detected on this network (no CLAT interface)
----------------------------
```
