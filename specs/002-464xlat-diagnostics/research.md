# Research: 464XLAT Enhanced Diagnostics

## NAT64 Prefix Discovery (RFC 7050)

**Decision**: Use the RFC 7050 `ipv4only.arpa` DNS method as the primary discovery path, with a well-known prefix connectivity probe as a secondary check.

**Rationale**: `ipv4only.arpa` has stable A records (192.0.0.170 and 192.0.0.171). When DNS64 synthesises a AAAA record for this name, the resulting IPv6 address encodes the NAT64 prefix per RFC 6052. This is the only standards-defined method. The well-known prefix `64:ff9b::/96` is probed separately because some carriers use it without DNS64 on the device-visible resolver.

**Prefix extraction algorithm (RFC 6052)**:
- Query `ipv4only.arpa` AAAA → synthesised address e.g. `64:ff9b::c000:00aa`
- Known embedded IPv4: `192.0.0.170` = `c0 00 00 aa`
- For /96 prefix: strip last 4 bytes → prefix = first 12 bytes
- For /64 prefix: bytes 0–7 are prefix, bytes 8–11 are IPv4, bytes 12–15 are zeros (RFC 6052 §2.2)
- For /48, /56, /40, /32 prefixes: progressively fewer prefix bytes
- In practice, carriers almost exclusively use /96 (simplest) or the well-known /96

**Well-known prefix probe**: Attempt to resolve a name with only an A record via the cellular resolver and observe if a synthesised AAAA is returned matching `64:ff9b::/96`.

## DNS64 Validation

**Decision**: Query `ipv4only.arpa` explicitly for AAAA records via the cellular DnsResolver (API 29+) or InetAddress fallback (API 26–28). Cross-check the embedded IPv4 against the known value (192.0.0.170).

**Rationale**: `ipv4only.arpa` is specifically reserved for this purpose (RFC 7050 §8.2). Using it ensures a deterministic known IPv4 value to decode from the response. No external hostname needed.

## CLAT Quality Measurement

**Decision**: MTU is read from `LinkProperties.getLinkMtu()` on the CLAT interface. Effective IPv4 payload MTU = CLAT MTU − 20 bytes (IPv6 header overhead). Latency comparison uses ICMP (ping) against the server on the CLAT path vs the native IPv6 path.

**Rationale**: `getLinkMtu()` is available API 26+. Active MTU probing via `tracepath`/`ping` with DF bit is not accessible without root. The 20-byte overhead is a fixed cost of the IPv6 encapsulation header added by CLAT.

## Embedded IPv4 Decoding

**Decision**: Decode client-side using the discovered NAT64 prefix per RFC 6052. For /96 prefix (the common case): the last 4 bytes of the 128-bit IPv6 address are the IPv4 address.

**Algorithm**:
```
prefix_len = 96  (most common)
ipv4_bytes = ipv6_address_bytes[12..15]  // for /96
ipv4 = InetAddress(ipv4_bytes)
```
For other prefix lengths, RFC 6052 §2.2 lookup table maps prefix length to IPv4 byte offset.

## Room Database Migration Strategy

**Decision**: Bump `AppDatabase` to version 2. Add new table `xlat_summaries` (new entity). No changes to existing tables.

**Migration SQL**:
```sql
CREATE TABLE IF NOT EXISTS xlat_summaries (
    session_id TEXT NOT NULL PRIMARY KEY,
    summary_json TEXT NOT NULL,
    overall_status TEXT NOT NULL,
    FOREIGN KEY(session_id) REFERENCES diagnostic_sessions(id) ON DELETE CASCADE
);
```

**Rationale**: Keeping XLAT data in a dedicated table with a 1:1 FK to sessions avoids adding nullable columns to existing tables (no ALTER TABLE needed for existing entities). New `TestType` values (NAT64_DISCOVERY, DNS64_VALIDATION, CLAT_QUALITY, PLAT_VERIFICATION) are added to the existing enum — Room stores enums as strings so no migration needed for that.

## TestFilter Extension

**Decision**: Add `XLAT_464` to the `TestFilter` enum. In `ALL` mode, XLAT sub-tests are included only when `NetworkInfo.clatPresent == true`.

**Rationale**: Matches the clarified UX (filter chip). Conditional inclusion in ALL avoids running discovery on networks without CLAT (SC-005: detect absence in <3s).

## Android API Compatibility

- `LinkProperties.getLinkMtu()` — API 29+. For API 26–28, fall back to reading `/sys/class/net/<iface>/mtu` via `BufferedReader` (world-readable on Android).
- `DnsResolver.query(TYPE_AAAA)` — API 29+. For API 26–28, use `InetAddress.getAllByName()` after `network.bindSocket()`.
- Both fallback paths are already established patterns in the existing `DnsTest.kt`.
