# M2 Tool Expansion

M2 expands the Structured Tool API from two tools to six. It adds `longClick`, `scroll`, `back`, and `home` while keeping the same safety contract that M1 established for `observe` and `click`.

M2 does **not** add an AI Agent, LLM integration, policy layer, network transport, or new UI. It also does **not** add `setText` or `screenshot`.

## Purpose

- Expose `AndroidActionExecutor.longClick`, `scroll`, `back`, and `home` through the same stable, typed boundary as `observe` and `click`.
- Ensure future callers still interact with the device only through `UiSnapshot`, `UiNode`, and `ToolResult` models.
- Apply the same stale-snapshot protection, fingerprint re-validation, post-action fresh observation, and password redaction to the new tools.

## Users

- **Primary future user:** AI Agent layer (not implemented in M2).
- **M2 verification users:**
  - `DebugOverlayController` in the `app` module (optional UI integration in WU-5).
  - `RancherDevHarnessScreen` in the `debug-harness` module (optional UI integration in WU-5).

The `structured-tool-api` module does **not** depend on `app`, `debug-harness`, Compose, or any debug UI.

## Exposed tools

The API exposes exactly six tools. The catalog is available programmatically via `StructuredToolApi.tools`.

### `observe()`

Unchanged from M1. Captures the current Android UI as a semantic `UiSnapshot`.

| Output field | Type | Description |
|--------------|------|-------------|
| `status` | `ToolStatus` | `SUCCESS`, `USER_ACTION_REQUIRED`, `FAILED`, etc. |
| `message` | `String?` | Human-readable result description. |
| `snapshot` | `UiSnapshot?` | The captured snapshot; `null` when capture fails. |
| `durationMs` | `Long` | Elapsed time in milliseconds. |

### `click(snapshotId: String, nodeId: Int)`

Unchanged from M1. Performs `AccessibilityNodeInfo.ACTION_CLICK` on the node identified by `snapshotId` and `nodeId`.

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

### `longClick(snapshotId: String, nodeId: Int)`

Performs `AccessibilityNodeInfo.ACTION_LONG_CLICK` on the node identified by `snapshotId` and `nodeId`.

| Input field | Type | Constraint |
|-------------|------|------------|
| `snapshotId` | `String` | Non-empty identifier of a snapshot. |
| `nodeId` | `Int` | Positive integer identifying a node within that snapshot. |

| Output field | Type | Description |
|--------------|------|-------------|
| `status` | `ToolStatus` | `SUCCESS`, `STALE_SNAPSHOT`, `NOT_FOUND`, `FAILED`, `TIMEOUT`, etc. |
| `message` | `String?` | Human-readable result description. |
| `previousSnapshotId` | `String?` | Snapshot ID before the long click. |
| `newSnapshotId` | `String?` | Snapshot ID after fresh observation; `null` if observation failed. |
| `durationMs` | `Long` | Elapsed time in milliseconds. |

Behavior:

- Delegates exactly once to `AndroidActionExecutor.longClick(snapshotId, nodeId)`.
- Uses `ACTION_LONG_CLICK` only; no coordinate taps, ADB, or shell commands.
- Rejects stale snapshots with `STALE_SNAPSHOT` before executing the action.
- Rejects missing nodes with `NOT_FOUND`.
- Rejects nodes whose captured fingerprint no longer matches the live UI with `STALE_SNAPSHOT`.
- After a successful long click, `AndroidActionExecutor` performs the fresh observation; the Structured Tool API does not capture again on its own.

### `scroll(snapshotId: String, nodeId: Int, direction: String)`

Scrolls the node identified by `snapshotId` and `nodeId` in the given direction using `ACTION_SCROLL_FORWARD` or `ACTION_SCROLL_BACKWARD`.

| Input field | Type | Constraint |
|-------------|------|------------|
| `snapshotId` | `String` | Non-empty identifier of a snapshot. |
| `nodeId` | `Int` | Positive integer identifying a node within that snapshot. |
| `direction` | `String` | Either `'forward'` or `'backward'`. |

| Output field | Type | Description |
|--------------|------|-------------|
| `status` | `ToolStatus` | `SUCCESS`, `STALE_SNAPSHOT`, `NOT_FOUND`, `NOT_SCROLLABLE`, `FAILED`, `TIMEOUT`, etc. |
| `message` | `String?` | Human-readable result description. |
| `previousSnapshotId` | `String?` | Snapshot ID before the scroll. |
| `newSnapshotId` | `String?` | Snapshot ID after fresh observation; `null` if observation failed. |
| `durationMs` | `Long` | Elapsed time in milliseconds. |

Behavior:

- Delegates exactly once to `AndroidActionExecutor.scroll(snapshotId, nodeId, direction)`.
- Uses `ACTION_SCROLL_FORWARD` or `ACTION_SCROLL_BACKWARD` only; no coordinate swipes, ADB, or shell commands.
- Rejects stale snapshots with `STALE_SNAPSHOT` before executing the action.
- Rejects missing nodes with `NOT_FOUND`.
- Rejects non-scrollable nodes with `NOT_SCROLLABLE` before executing the action.
- Rejects nodes whose captured fingerprint no longer matches the live UI with `STALE_SNAPSHOT`.
- After a successful scroll, `AndroidActionExecutor` performs the fresh observation; the Structured Tool API does not capture again on its own.

### `back()`

Presses the system BACK button via `performGlobalAction(GLOBAL_ACTION_BACK)`.

This tool has **no inputs** because it is a global action that is not tied to a node.

| Output field | Type | Description |
|--------------|------|-------------|
| `status` | `ToolStatus` | `SUCCESS`, `FAILED`, `TIMEOUT`, etc. |
| `message` | `String?` | Human-readable result description. |
| `previousSnapshotId` | `String?` | Snapshot ID before the back action (current snapshot at call time). |
| `newSnapshotId` | `String?` | Snapshot ID after fresh observation; `null` if observation failed. |
| `durationMs` | `Long` | Elapsed time in milliseconds. |

Behavior:

- Delegates exactly once to `AndroidActionExecutor.back()`.
- Uses `GLOBAL_ACTION_BACK` only; no coordinate taps, ADB, or shell commands.
- Uses the snapshot that was current at call time as `previousSnapshotId`.
- After a successful global action, `AndroidActionExecutor` performs the fresh observation; the Structured Tool API does not capture again on its own.

### `home()`

Presses the system HOME button via `performGlobalAction(GLOBAL_ACTION_HOME)`.

This tool has **no inputs** because it is a global action that is not tied to a node.

| Output field | Type | Description |
|--------------|------|-------------|
| `status` | `ToolStatus` | `SUCCESS`, `FAILED`, `TIMEOUT`, etc. |
| `message` | `String?` | Human-readable result description. |
| `previousSnapshotId` | `String?` | Snapshot ID before the home action (current snapshot at call time). |
| `newSnapshotId` | `String?` | Snapshot ID after fresh observation; `null` if observation failed. |
| `durationMs` | `Long` | Elapsed time in milliseconds. |

Behavior:

- Delegates exactly once to `AndroidActionExecutor.home()`.
- Uses `GLOBAL_ACTION_HOME` only; no coordinate taps, ADB, or shell commands.
- Uses the snapshot that was current at call time as `previousSnapshotId`.
- After a successful global action, `AndroidActionExecutor` performs the fresh observation; the Structured Tool API does not capture again on its own.

## Safety properties

The same safety principles that M1 established for `observe` and `click` apply to the four new tools:

- **Stale snapshot protection:** `longClick` and `scroll` reject a non-current `snapshotId` with `STALE_SNAPSHOT` before any Accessibility action is performed. `back` and `home` use the current snapshot at call time as `previousSnapshotId` but do not require a snapshot/node argument.
- **Fingerprint re-validation:** `longClick` and `scroll` resolve the target node through `UiSnapshotEngine.resolve()`, which re-fetches the live node by path and verifies the captured semantic fingerprint (viewId, text, className, bounds within ±8 px, clickable/enabled). A mismatch returns `STALE_SNAPSHOT`.
- **NOT_SCROLLABLE rejection:** `scroll` checks the captured `UiNode.scrollable` flag before resolving or performing the action. If the node is not scrollable, it returns `NOT_SCROLLABLE` without executing `ACTION_SCROLL_FORWARD`/`ACTION_SCROLL_BACKWARD`.
- **Fresh observation:** Every successful `longClick`, `scroll`, `back`, and `home` triggers exactly one post-action fresh observation through `UiSnapshotEngine.capture()`. The Structured Tool API layer does not perform its own capture.
- **Fresh observation failure:** If the post-action capture fails, the result status is `FAILED` (when a UI change event was observed) or `TIMEOUT` (when no event arrived), matching the existing `click` behavior.
- **Password redaction:** `observe` continues to return snapshots where password node `text` and `contentDescription` are `[REDACTED]`. Because `longClick`, `scroll`, `back`, and `home` return only snapshot IDs, redaction is preserved by the same `UiSnapshotEngine` path that produces the fresh snapshot.
- **Single delegation:** The Structured Tool API implementation delegates exactly once to the underlying executor and passes the result through unmodified. It does not bypass `STALE_SNAPSHOT`, `NOT_FOUND`, `NOT_SCROLLABLE`, `FAILED`, or `TIMEOUT` results.

## Layer boundary

```
AI Agent (future)
    |
    v
Structured Tool API  <-- M2 boundary (observe, click, longClick, scroll, back, home)
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

## Out of scope (M2)

- AI Agent / planner / task engine / scheduler / long-term memory.
- Policy / safety / authorization layer.
- LLM / function-calling integration.
- HTTP / WebSocket / JSON-RPC / MCP / remote control.
- Chat UI or external tool consumers.
- JSON serialization infrastructure.
- Tools other than `observe`, `click`, `longClick`, `scroll`, `back`, and `home` (e.g., `setText`, `screenshot`).
- Exposure of raw `AccessibilityNodeInfo` or other Android objects.
- Coordinate tapping, ADB, or shell-based operations.
- Changes to the existing `click` and `observe` behaviors.
- Changes to existing models, password redaction, or fresh-observation semantics.

## Key files

- `structured-tool-api/src/main/java/dev/rancher/tool/api/StructuredToolApi.kt` — interface and six-tool catalog.
- `structured-tool-api/src/main/java/dev/rancher/tool/api/ToolDefinition.kt` — tool metadata and schema shapes.
- `structured-tool-api/src/main/java/dev/rancher/tool/api/ObserveToolResult.kt` — result type for `observe()`.
- `structured-tool-api/src/main/java/dev/rancher/tool/api/AndroidStructuredToolApi.kt` — production implementation delegating to the Android Control Engine.
- `structured-tool-api/src/test/java/dev/rancher/tool/api/StructuredToolApiTest.kt` — unit tests for delegation, success/failure, stale passthrough, and tool catalog.
- `android-actions/src/main/java/dev/rancher/android/actions/AndroidActionExecutor.kt` — executor implementation for `longClick`, `scroll`, `back`, and `home`.
- `android-actions/src/test/java/dev/rancher/android/actions/AndroidActionExecutorTest.kt` — executor safety-contract tests.
- `core-model/src/main/java/dev/rancher/core/model/ToolResult.kt` — `ToolStatus` includes `NOT_SCROLLABLE`.

## Verification

- `:structured-tool-api:testDebugUnitTest` passes (26 tests including six-tool catalog and delegation tests).
- `:android-actions:testDebugUnitTest` passes (26 tests including `longClick`, `scroll`, `back`, and `home`).
- Full `./gradlew.bat test` and `./gradlew.bat assembleDebug` pass.
- Emulator or physical device confirmation of `observe → action → fresh snapshot` for each new tool is performed after WU-5 (UI integration) and recorded in `TASK.md`.

See `TASK.md` for the detailed acceptance criteria and execution results.
