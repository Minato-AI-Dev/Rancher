# M0 Architecture Notes

## Runtime path

```text
RancherAccessibilityService
        ↓
AccessibilityBridge
        ↓
UiSnapshotEngine
        ↓
UiSnapshot / UiNode
        ↓
RancherDevHarnessScreen / M0 accessibility overlay
        ↓ user selects CLICK
AndroidActionExecutor
        ↓ stale-snapshot + node fingerprint check
AccessibilityNodeInfo.ACTION_CLICK
        ↓
Accessibility event / timeout
        ↓
UiSnapshotEngine.capture()
        ↓
fresh UiSnapshot
```

## Snapshot identity

- Snapshot IDs are process-local monotonically increasing IDs such as `snap_000001`.
- Semantic node IDs begin at 1 and are valid only for the current snapshot.
- `UiSnapshotEngine.resolve()` rejects any action whose snapshot ID is not the current one.
- A node is resolved from an internal child-index path, then checked against a semantic fingerprint before use.

This avoids handing long-lived `AccessibilityNodeInfo` references to higher layers and prevents a stale node ID from silently acting on a different screen.

## Compression

Only visible nodes that are actionable or carry semantic information are retained. M0 keeps nodes that are clickable, long-clickable, editable, scrollable, selected, checked, labeled, or have a view ID. The raw accessibility tree remains internal.

## Sensitive text

If Android marks a node as a password field, both text and content description are replaced with `[REDACTED]` before entering the semantic model.

## M0 intentionally deferred

- Generic `AgentTool` interface
- LLM/model providers
- Policy/confirmation engine beyond stale-snapshot protection
- setText / scrolling / back / home tool implementations
- screenshots / vision
- persistence, tasks, scheduler, chat
- overlay kill switch

Those belong after the Settings → Connected devices path has been validated repeatedly on a real device.

## Why the M0 overlay exists

Bringing the Rancher Activity to the foreground would make Rancher itself the active accessibility window, so a Settings node could become stale before the developer presses CLICK. The M0 demo therefore uses a developer-only `TYPE_ACCESSIBILITY_OVERLAY`. It leaves Android Settings as the active target while exposing Refresh/CLICK controls. This overlay is test infrastructure, not the future product UI or the kill switch.
