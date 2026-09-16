# Rancher — M0 Android Control Harness

Rancher M0 is a native Kotlin Android prototype that proves one end-to-end control loop:

1. Enable `RancherAccessibilityService`.
2. Open Android Settings.
3. Observe the active accessibility tree.
4. Convert it to a Rancher-owned semantic `UiSnapshot`.
5. Display actionable nodes in a Jetpack Compose developer harness.
6. Click a selected node (for the first demo, **Connected devices**).
7. Wait for Android UI change events.
8. Produce and display a fresh semantic snapshot.

This repository intentionally does **not** contain an LLM, chat, scheduling, long-term memory, remote control, root/ADB, coordinate tapping, password automation, or payment/purchase behavior.

## Modules

- `app` — launcher activity only.
- `core-model` — immutable Rancher models (`UiNode`, `UiSnapshot`, `ToolResult`).
- `android-accessibility` — Android `AccessibilityService` and service/event bridge.
- `android-snapshot` — accessibility-tree traversal, compression, redaction, snapshot IDs, stale-snapshot resolution.
- `android-actions` — semantic click execution and post-action re-observation.
- `debug-harness` — developer-only Compose UI.
- `app` also hosts a developer accessibility overlay for exercising the harness while the target app remains active.

## Build requirements

- Android Studio with Android SDK installed.
- JDK 17+.
- Android SDK API 37 and Build Tools 36.x (AGP 9.4 supports API 37).
- Gradle 9.6.

> The source bundle does not include the binary `gradle-wrapper.jar`. If your checkout does not already have it, let Android Studio generate the wrapper once (or run `gradle wrapper --gradle-version 9.6.0` from a machine with Gradle installed).

## Run the M0 demo

1. Build and install the `app` module on a physical Android device or emulator.
2. Launch Rancher.
3. Tap **Accessibility Settings** and enable **Rancher**.
4. Return to Rancher and tap **M0 Settings demo**.
5. Rancher opens Android Settings and keeps a developer `TYPE_ACCESSIBILITY_OVERLAY` visible so Settings remains the active target.
6. In the overlay, tap **Refresh**.
7. Find **Connected devices** and tap **CLICK**.
8. Rancher waits for a target-app UI event, creates a fresh snapshot, and updates the overlay with the changed Settings screen.

## Safety properties already enforced in M0

- Raw `AccessibilityNodeInfo` objects never appear in `core-model` or the debug UI.
- Every node ID is scoped to one snapshot.
- Actions reject non-current snapshot IDs with `STALE_SNAPSHOT`.
- Node resolution re-validates the current node against its captured semantic fingerprint before clicking.
- Password node text/content descriptions are redacted.
- Coordinate taps are not used.
- A fresh observation is attempted after every successful click.

## M0 acceptance target

The first target is Android Settings → **Connected devices**. Once that path is reliable on a real device, the next milestone can introduce the generic structured tool API. Do not add an LLM before that validation.
