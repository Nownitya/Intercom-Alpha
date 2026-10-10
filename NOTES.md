# Technical Notes & Architectural Gotchas

> **Obsidian Source:** `D:\ObsidianVault\Vault1\ObsidianVault\Android\Projects\Intercom-Alpha\07-Permanent-Notes\`  
> **MemPalace Integration:** Wing `intercom_alpha` (2,426 drawers)

---

## 📡 Cross-Platform GATT Protocol
* **Service UUID:** `0000180d-0000-1000-8000-00805f9b34fb` (defined in `MeshTransport.kt:GATT_SERVICE_UUID`)
* **Characteristic UUID:** `00002a37-0000-1000-8000-00805f9b34fb` (defined in `MeshTransport.kt:GATT_CHARACTERISTIC_UUID`)
* **BLE Manufacturer ID:** `0x0991` (defined in `MeshTransport.kt:BLE_MANUFACTURER_ID`)
* **Payload Encoding:** UTF-8 JSON text encoded to ByteArray.
* **TTL Flooding:** Max hops = 3. Nodes decrement TTL and re-broadcast to all peers except immediate sender.

---

## 🎙️ Audio Pipeline & Codec
* **Default Profile:** 24 kHz, 1 channel (mono), 20 ms frame size (480 samples), 32 kbps bitrate.
* **PCM Little-Endian Byte Order:**
  * Encode: Short sample `(sample and 0xFF)` (low byte), `((sample shr 8) and 0xFF)` (high byte).
  * Decode: `((high shl 8) or low).toShort()`.
* **VOX Threshold:** Default -40 dB. RMS calculated over frame sample buffers.

---

## 🎧 Headset Audio Routing
* **Android:** Requires `AudioManager.startBluetoothSco()` and listening to `ACTION_SCO_AUDIO_STATE_UPDATED`.
* **iOS:** Requires `AVAudioSessionCategoryPlayAndRecord` with `AVAudioSessionCategoryOptionAllowBluetooth` and `setPreferredInput(port)`.

---

## 🧠 Memory & Knowledge System
* **Obsidian Vault:** `D:\ObsidianVault\Vault1\ObsidianVault\Android\Projects\Intercom-Alpha\`
* **MemPalace (Official v3.10.0):**
  * Official Website: `https://mempalaceofficial.com/`
  * Official GitHub: `https://github.com/mempalace/mempalace` (Release: `v3.10.0`)
  * Antigravity Guide: `https://mempalaceofficial.com/guide/antigravity.html`
  * *Security Note:* Only use `mempalaceofficial.com`. Unofficial mirrors (e.g. `.net`, `.tech`) are flagged as third-party impostor sites.
* **Graphify Canvas:** `graphify export obsidian --dir ...` exports 1,026 notes and interactive `graph.canvas`.
* **Graphify Wiki:** `graphify export wiki` creates 71 cross-linked articles in `graphify-out/wiki/index.md`.

---

## 🔀 Multi-Path Failover & Dual-Stream Hedging Gotchas (v2.12.0)
* **Link Quality Thresholds ($Q_{link}$)**:
  * $Q_{link} \ge 70$: `BLE_PRIMARY` (Cellular SFU transport in idle heartbeat).
  * $30 \le Q_{link} < 70$: `DEGRADED_HEDGING` (Both BLE and Cellular actively transmitting duplicate audio packets).
  * $Q_{link} < 30$ sustained for 1.5s: `CELLULAR_ACTIVE` (BLE transmission suspended).
  * BLE $RSSI > -75\text{ dBm}$ for 3.0s: Transition to `RECOVERING` and back to `BLE_PRIMARY`.
* **Zero-Drop Deduplication**: Crucial that `PacketDeduplicator.kt` processes packets from both transports through the exact same sequence-number cache so that the first arriving packet is played immediately and the second duplicate is dropped with zero latency.
* **Cellular Data Protection**: Never run stream hedging permanently; enforce a strict 15-second timeout on unresolvable hedging to drop to `CELLULAR_ACTIVE` or back to `BLE_PRIMARY` to protect rider mobile data caps.

---

## 🎚️ Airtime QoS Priority Shaping Gotchas (v2.11.0)
* **Preemption Guarantee**: `Priority 0: EMERGENCY` packets (eCall SOS, crash alert) must unconditionally abort non-voice BLE advertisement cycles.
* **Voice Ceiling**: Voice packets (`Priority 1`) must never spend $> 40\text{ms}$ in software transmit queues. If queue depth threatens jitter buffer deadlines, discard oldest telemetry frame, never voice.
* **WFQ Ratio**: Secondary queues (Priority 2 Control vs Priority 3 Telemetry) operate on a 60/40 weighted round-robin ratio to avoid starving AODV-Lite route repair frames.
