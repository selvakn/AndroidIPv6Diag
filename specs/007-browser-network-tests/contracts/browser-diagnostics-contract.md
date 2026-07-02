# Contract: Browser Diagnostics Configuration API

## Endpoint
- `GET /browser-diagnostics/config`

## Purpose
- Provide browser clients with default test targets and runtime policy required to execute diagnostics in client context.

## Request
- Method: `GET`
- Auth: none (public endpoint for this feature)
- Body: none

## Response 200 (application/json)

```json
{
  "public_access": true,
  "allow_custom_targets": true,
  "per_test_timeout_ms": 15000,
  "rate_limiting_enabled": false,
  "default_targets": [
    {
      "test_type": "HTTP",
      "label": "Primary HTTP Diag",
      "value": "http://example.net/diag",
      "origin": "default",
      "enabled_by_default": true
    }
  ],
  "turn_credential_mode": "tokenless_endpoint"
}
```

## Field Semantics
- `public_access`: Indicates page and execution are intentionally unauthenticated.
- `allow_custom_targets`: Enables user-entered destinations.
- `per_test_timeout_ms`: Timeout to apply independently per test.
- `rate_limiting_enabled`: Always false for this release.
- `default_targets`: Baseline test destinations grouped by protocol.
- `turn_credential_mode`: How TURN credentials are sourced for browser TURN checks.

## Error Responses
- `405 Method Not Allowed` for non-GET methods.
- `500 Internal Server Error` if server cannot construct valid config payload.

## Compatibility Notes
- Unknown top-level fields must be ignored by clients for forward compatibility.
- Missing required fields should fail closed in UI with a visible configuration error.
