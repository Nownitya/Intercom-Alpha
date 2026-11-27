# Sprint 38 → ICA-3801 — Basecamp Console

GSD roadmap reference is Phase 33. The owner approved the console direction on 2026-11-25. The exit condition is an operator connecting to a phone running Intercom-Alpha in stationary BLE repeater mode, inspecting forwarding, running a link check, and recovering from disconnection.

## Product constraints

Riders join with their phones. They can use the phone mic and speaker or a Bluetooth audio device supported by the phone for calls. The audio accessory connects to the rider's phone. Intercom-Alpha uses BLE between phones and mesh forwarding to reach riders beyond a direct link. Optional VOX detects speech and controls transmission within the audio session.

Basecamp is optional for group rides. For Sprint 38, use an Android phone running the app's existing stationary repeater mode as the first verification target. Riders do not need dedicated repeater hardware or a USB accessory.

## 1. Prepare the sprint and verify the phone interface

- Integrate the Sprint 37 closeout into the local integration branch before creating the Sprint 38 branch from it. Preserve the existing working changes. Create the Sprint 38 Ralph queue on that branch and take one story at a time.
- Verify the Android phone in stationary repeater mode against the existing MeshTransport GATT contract and MeshRepeaterService beacon and ping/pong formats. Determine which desktop and browser environments can use that BLE connection. Record the app build, Android version, permissions, and evidence of connection, telemetry, and a diagnostic round trip.

## 2. Build and test the shared connection state

- Use the existing repeater telemetry model and diagnostics where compatible. Read MeshRepeaterService, RepeaterViewModel, and the IntercomHardwareBridge contract before introducing a new module. RepeaterViewModel defaults to a no-op hardware bridge and starts a local repeater. A remote connection needs its own observable state and device-backed events.
- Test a successful connection, forwarding telemetry, a matching diagnostic reply, timeout, unplug or lost connection, and a clean reconnect. Clear incompatible pending requests and label previously received data as stale when connectivity drops. Cover unsupported hosts and permission denial.
- Implement the first supported transport adapter against the verified protocol. Only surface transports that the host supports. Keep OS and browser capabilities in platform-specific code and share the connection state and packet handling.

## 3. Connect the console and verify the exit condition

- Use the existing Compose desktop and web App entry points. Link the console from normal navigation and present connected, connecting, disconnected, and unsupported states. Expose device connection, forwarding metrics, and a link check with success, timeout, and retry feedback.
- Run the required Gradle tests, Android and iOS simulator compile gates, and desktop/web build checks. Exercise the entire operator flow on each supported host with the Android phone in repeater mode, including Bluetooth loss and reconnection. Record the phone, Android version, app build, and host versions alongside results.
- Run Graphify and the OpenCodeReview preview and rule gates before committing. Record findings and address the required severities. Advance to fleet monitoring when ICA-3801 passes its exit condition.
