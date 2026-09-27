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
