# Data Model: Browser-Based Network Test Suite

## Entity: DiagnosticRun
- **Description**: One user-triggered execution cycle from the browser diagnostics UI.
- **Fields**:
  - `runId` (string, required): unique identifier for the run within the browser session.
  - `startedAt` (RFC3339 timestamp, required): run start time.
  - `finishedAt` (RFC3339 timestamp, optional): run completion time when all selected tests reach terminal state.
  - `clientContext` (object, optional): browser/network context summary (user agent, language, timezone, online status).
  - `selectedTests` (array of enum, required): subset of `HTTP|HTTPS|ICMP_EQUIV|STUN|TURN`.
  - `results` (array of `TestResult`, required): one result per selected test.
  - `status` (enum, required): `pending|running|completed|partial`.

## Entity: TestTarget
- **Description**: Destination definition used by a given test.
- **Fields**:
  - `testType` (enum, required): `HTTP|HTTPS|ICMP_EQUIV|STUN|TURN`.
  - `label` (string, required): human-readable display name.
  - `value` (string, required): URL or ICE server URI.
  - `origin` (enum, required): `default|custom`.
  - `enabledByDefault` (boolean, required): whether pre-selected in UI defaults.

## Entity: TestResult
- **Description**: Terminal or in-progress outcome for one selected test in a run.
- **Fields**:
  - `testType` (enum, required): test category.
  - `target` (`TestTarget`, required): exact target used for this execution.
  - `status` (enum, required): `pending|running|passed|failed|timed_out|unsupported`.
  - `startedAt` (RFC3339 timestamp, required): per-test start time.
  - `endedAt` (RFC3339 timestamp, optional): per-test end time.
  - `durationMs` (integer, optional): elapsed runtime in milliseconds.
  - `reason` (string, optional): human-readable failure/unsupported/timeout explanation.
  - `evidence` (string, optional): concise diagnostic proof text (status code, candidate found, etc.).

## Entity: BrowserDiagnosticsConfig
- **Description**: Server-provided configuration payload consumed by the web page.
- **Fields**:
  - `publicAccess` (boolean, required): whether unauthenticated access is allowed.
  - `allowCustomTargets` (boolean, required): whether runtime custom targets are enabled.
  - `perTestTimeoutMs` (integer, required): timeout applied to each selected test.
  - `rateLimitingEnabled` (boolean, required): expected false in this release.
  - `defaultTargets` (array of `TestTarget`, required): baseline targets by protocol type.
  - `turnCredentialMode` (enum, required): `none|tokenless_endpoint|static` (how browser gets TURN credentials).

## Relationships
- One `DiagnosticRun` contains many `TestResult` records.
- Each `TestResult` references one `TestTarget`.
- `BrowserDiagnosticsConfig` seeds available `TestTarget` definitions for run construction.

## Validation Rules
- `selectedTests` must not be empty for run start.
- Exactly one `TestResult` must exist for each selected test type.
- `durationMs` must be non-negative when `endedAt` exists.
- `status` must be terminal (`passed|failed|timed_out|unsupported`) before run status can become `completed|partial`.
- TURN and STUN targets must include valid ICE URI format recognized by browser WebRTC APIs.
