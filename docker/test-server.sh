#!/bin/sh
# Verifies the IPv6 diagnostic server endpoints from inside the compose network.
# Exits 0 if all tests pass, 1 if any fail.

set -e

SERVER_IPV4="${SERVER_IPV4:-172.28.0.10}"
SERVER_IPV6="${SERVER_IPV6:-fd00:cafe::10}"
PORT="${SERVER_PORT:-80}"
PASS=0
FAIL=0

apk add --no-cache curl >/dev/null 2>&1

pass() { echo "  PASS  $1"; PASS=$((PASS+1)); }
fail() { echo "  FAIL  $1 — $2"; FAIL=$((FAIL+1)); }

run_test() {
    local label="$1"
    local url="$2"
    local expected_family="$3"

    result=$(curl -sf --max-time 5 "$url" 2>/dev/null) || { fail "$label" "connection failed"; return; }

    # Validate JSON fields
    af=$(echo "$result" | grep -o '"address_family":"[^"]*"' | cut -d'"' -f4)
    proto=$(echo "$result" | grep -o '"protocol":"[^"]*"' | cut -d'"' -f4)
    server_addr=$(echo "$result" | grep -o '"server_address":"[^"]*"' | cut -d'"' -f4)
    client_addr=$(echo "$result" | grep -o '"client_address":"[^"]*"' | cut -d'"' -f4)
    ts=$(echo "$result" | grep -o '"timestamp":"[^"]*"' | cut -d'"' -f4)

    if [ -z "$af" ] || [ -z "$proto" ] || [ -z "$server_addr" ] || [ -z "$client_addr" ] || [ -z "$ts" ]; then
        fail "$label" "missing JSON fields — got: $result"
        return
    fi
    if [ "$af" != "$expected_family" ]; then
        fail "$label" "expected address_family=$expected_family, got $af"
        return
    fi
    if [ "$proto" != "HTTP" ]; then
        fail "$label" "expected protocol=HTTP, got $proto"
        return
    fi
    pass "$label (server=$server_addr client=$client_addr ts=$ts)"
}

echo ""
echo "=== IPv6 Diagnostic Server Tests ==="
echo "  Server IPv4 : $SERVER_IPV4:$PORT"
echo "  Server IPv6 : [$SERVER_IPV6]:$PORT"
echo ""

echo "--- /health ---"
result=$(curl -sf --max-time 5 "http://$SERVER_IPV4:$PORT/health" 2>/dev/null) || result=""
[ "$result" = "ok" ] && pass "IPv4 /health" || fail "IPv4 /health" "expected 'ok', got '$result'"

result=$(curl -sf --max-time 5 "http://[$SERVER_IPV6]:$PORT/health" 2>/dev/null) || result=""
if [ -z "$result" ]; then
    echo "  SKIP  IPv6 /health — IPv6 may not be available in this environment"
else
    [ "$result" = "ok" ] && pass "IPv6 /health" || fail "IPv6 /health" "expected 'ok', got '$result'"
fi

echo ""
echo "--- /diag ---"
run_test "IPv4 /diag" "http://$SERVER_IPV4:$PORT/diag" "IPv4"

ipv6_result=$(curl -sf --max-time 5 "http://[$SERVER_IPV6]:$PORT/diag" 2>/dev/null) || ipv6_result=""
if [ -z "$ipv6_result" ]; then
    echo "  SKIP  IPv6 /diag — IPv6 not available (enable IPv6 in Docker daemon)"
else
    af=$(echo "$ipv6_result" | grep -o '"address_family":"[^"]*"' | cut -d'"' -f4)
    if [ "$af" = "IPv6" ]; then
        pass "IPv6 /diag (address_family=IPv6)"
    else
        fail "IPv6 /diag" "expected address_family=IPv6, got $af"
    fi
fi

echo ""
echo "--- Method validation ---"
bad=$(curl -sf -X POST --max-time 5 "http://$SERVER_IPV4:$PORT/diag" 2>/dev/null) || bad=""
code=$(curl -so /dev/null -w "%{http_code}" -X POST --max-time 5 "http://$SERVER_IPV4:$PORT/diag" 2>/dev/null) || code=0
[ "$code" = "405" ] && pass "POST /diag returns 405" || fail "POST /diag" "expected 405, got $code"

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
echo ""
[ "$FAIL" -eq 0 ]
