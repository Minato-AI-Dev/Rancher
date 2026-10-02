# M1 Structured Tool API

M1 introduces a stable, typed internal boundary — the **Structured Tool API** — between future AI Agent callers and the existing Android Control Engine. M1 does **not** add an AI Agent, LLM integration, policy layer, network transport, or new UI.

## Purpose

- Expose the existing `UiSnapshotEngine.capture()` and `AndroidActionExecutor.click(snapshotId, nodeId)` as explicit, callable tools.
- Ensure future callers interact with the device only through `UiSnapshot`, `UiNode`, and `ToolResult` models.
- Prevent `AccessibilityNodeInfo`, Compose nodes, Views, or other Android-specific objects from leaking through the API.

## Users

- **Primary future user:** AI Agent layer (not implemented in M1).
- **M1 verification users:**
  - `DebugOverlayController` in the `app` module.
  - `RancherDevHarnessScreen` in the `debug-harness` module.

The Structured Tool API module does **not** depend on `app`, `debug-harness`, Compose, or any debug UI.

## Exposed tools

The API exposes exactly two tools. The catalog is available programmatically via `StructuredToolApi.tools`.

### `observe()`

Captures the current Android UI as a semantic `UiSnapshot`.

| Output field | Type | Description |
|--------------|------|-------------|
| `status` | `ToolStatus` | `SUCCESS`, `USER_ACTION_REQUIRED`, `FAILED`, etc. |
| `message` | `String?` | Human-readable result description. |
| `snapshot` | `UiSnapshot?` | The captured snapshot; `null` when capture fails. |
| `durationMs` | `Long` | Elapsed time in milliseconds. |

Behavior:

- If the `AccessibilityService` is not connected, returns `USER_ACTION_REQUIRED` with a clear enablement message.
- If connected and `UiSnapshotEngine.capture()` returns a snapshot, returns `SUCCESS` and the snapshot.
- If connected and capture returns `null`, returns `FAILED` (never an unhandled exception or meaningless `null`).

### `click(snapshotId: String, nodeId: Int)`

Performs `AccessibilityNodeInfo.ACTION_CLICK` on the node identified by `snapshotId` and `nodeId`.

| Input field | Type | Constraint |
|-------------|------|------------|
| `snapshotId` | `String` | Non-empty identifier of a snapshot. |
| `nodeId` | `Int` | Positive integer identifying a node within that snapshot. |

| Output field | Type | Description |
|--------------|------|-------------|
| `status` | `ToolStatus` | `SUCCESS`, `STALE_SNAPSHOT`, `NOT_FOUND`, `FAILED`, `TIMEOUT`, etc. |
| `message` | `String?` | Human-readable result description. |
| `previousSnapshotId` | `String?` | Snapshot ID before the click. |
| `newSnapshotId` | `String?` | Snapshot ID after fresh observation; `null` if observation failed. |
| `durationMs` | `Long` | Elapsed time in milliseconds. |

Behavior:

- Delegates exactly once to `AndroidActionExecutor.click(snapshotId, nodeId)`.
- Does not introduce coordinate taps, ADB, shell commands, or alternative identifiers.
- Passes through `STALE_SNAPSHOT`, `NOT_FOUND`, and fingerprint-mismatch results without bypassing them.
- After a successful click, `AndroidActionExecutor` performs the fresh observation; the Structured Tool API does not capture again on its own.

## Layer boundary

```
AI Agent (future)
    |
    v
Structured Tool API  <-- M1 boundary
    |
    v
Android Control Engine (UiSnapshotEngine, AndroidActionExecutor)
    |
    v
AccessibilityService -> Android Device
```

The `structured-tool-api` module depends only on:

- `core-model` for `UiSnapshot`, `UiNode`, `ToolResult`, `ToolStatus`.
- `android-accessibility` for `AccessibilityBridge` connection state.
- `android-snapshot` for `UiSnapshotEngine`.
- `android-actions` for `AndroidActionExecutor`.

It does **not** depend on `app`, `debug-harness`, Compose, or any UI framework.

## Out of scope (M1)

- AI Agent / planner / task engine / scheduler / long-term memory.
- Policy / safety / authorization layer.
- LLM / function-calling integration.
- HTTP / WebSocket / JSON-RPC / MCP / remote control.
- Chat UI or external tool consumers.
- JSON serialization infrastructure.
- Tools other than `observe` and `click` (e.g., `setText`, `scroll`, `longClick`, `back`, `home`, `screenshot`).
- Exposure of raw `AccessibilityNodeInfo` or other Android objects.
- Coordinate tapping, ADB, or shell-based operations.
- Changes to existing models, stale-snapshot protection, password redaction, `ACTION_CLICK`, or fresh-observation semantics.

## Key files

- `structured-tool-api/src/main/java/dev/rancher/tool/api/StructuredToolApi.kt` — interface and tool catalog.
- `structured-tool-api/src/main/java/dev/rancher/tool/api/ToolDefinition.kt` — tool metadata and schema shapes.
- `structured-tool-api/src/main/java/dev/rancher/tool/api/ObserveToolResult.kt` — result type for `observe()`.
- `structured-tool-api/src/main/java/dev/rancher/tool/api/AndroidStructuredToolApi.kt` — production implementation delegating to the Android Control Engine.
- `structured-tool-api/src/test/java/dev/rancher/tool/api/StructuredToolApiTest.kt` — unit tests for delegation, success/failure, stale passthrough, password redaction, and tool catalog.

## Verification

- `:structured-tool-api:testDebugUnitTest` passes.
- `:app:assembleDebug` passes (Debug Overlay uses the API).
- `:debug-harness:assembleDebug` passes (Developer Harness uses the API).
- Full `./gradlew.bat test` and `./gradlew.bat assembleDebug` pass.

See `TASK.md` for the detailed acceptance criteria and execution results.
