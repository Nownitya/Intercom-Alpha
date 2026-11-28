# System Structure Document (SSD) — Intercom-Alpha

> **Document Version:** 3.0.0-beta1  
> **Status:** Active System Blueprint  
> **Scope:** Architectural Subsystems, Concurrency Models, Data Flow & State Machines  
> **Repository:** `Intercom-Alpha`  

---

## 1. System Structural Topology & Module Decomposition

Intercom-Alpha is organized into a modular, decoupled hierarchy enforcing strict multiplatform boundaries:

```
                            +--------------------+
                            |    :app:iosApp     |
                            |  (Native SwiftUI)  |
                            +--------------------+
                                      |
                           (IntercomBridge.kt)
                                      |
+--------------------+      +--------------------+      +--------------------+
|  :app:androidApp   |      |  :app:sharedLogic  |      |   :app:sharedUI    |
| (Android Manifest, | ---> | (KMP Engine & DSP) | <--- | (Compose Cockpit   |
| Foreground Service)|      +--------------------+      |  Android/Desktop)  |
+--------------------+                |                 +--------------------+
                                      |
                            +--------------------+
                            |       :core        |
                            | (Signaling Models) |
                            +--------------------+
                                      |
                            +--------------------+
                            |      :server       |
                            | (Ktor SFU Relay)   |
                            +--------------------+
```

### Module Boundary Invariants
1. **Rule 1 (No Shared UI on iOS):** `:app:sharedUI` is strictly excluded from iOS build targets. iOS presentation is 100% Native SwiftUI in `:app:iosApp`.
2. **Rule 2 (Shared Logic in KMP):** All packet routing, DSP algorithms, failover logic, offline vector tile decoders, elevation engines, and encryption reside in `:app:sharedLogic/src/commonMain`.
3. **Rule 3 (Pure Multiplatform Cryptography):** Cryptography has zero `java.security.*` or native C dependencies (RFC 8439 pure Kotlin).

---

## 2. End-to-End Data Flow Architectures

### 2.1 Audio Transmission Data Pipeline (Tx Pipeline)
```
[Microphone Ingestion (16kHz PCM)]
                |
                v
[Adaptive Energy VOX & Noise Gate] ---- (Below -35dBFS) ----> [Drop Silence]
                | (Above Threshold)
                v
[Dynamic Bitrate & Frame Sizer] <--- [Link Quality Score Q_link]
                | (16kbps / 32kbps / 48kbps)
                v
[Multi-Rate Codec Compression (ADPCM / Opus)]
                | (Compressed Audio Frame)
                v
[Block Erasure FEC Engine] ----------> [Generate Parity Frames (4:1, 3:1, 2:1)]
                |
                v
[Time-Diversity Matrix Block Interleaver] (M x N dispersion against burst loss)
                |
                v
[ATT MTU Frame Aggregator] (Pack 2–4 frames up to 247 bytes)
                |
                v
[Pure Kotlin ChaCha20-Poly1305 Encryptor] (Key Epoch + Monotonic Seq Nonce)
                |
                v
[Airtime QoS Token-Bucket Shaper] (Priority 0: SOS / Priority 1: Voice)
                |
                v
[Mesh Congestion Governor & Dynamic Backoff] (Channel Airtime Throttling)
                |
                v
[BLE GATT Flooding Transport (TTL=3)] & [Optional Cellular SFU Stream Hedging]
```

### 2.2 Audio Reception Data Pipeline (Rx Pipeline)
```
[BLE GATT Characteristic Rx] / [Cellular WebSocket Rx]
                |
                v
[Sliding-Window Packet Deduplicator] ---- (Duplicate Sequence) ----> [Discard]
                | (Fresh Frame)
                v
[Pure Kotlin ChaCha20-Poly1305 Decrypt & MAC Verify] ---- (Bad MAC) --> [Discard]
                | (Verified Frame)
                v
[ATT MTU Frame De-Aggregator] (Unpack multiple frames from single payload)
                |
                v
[Matrix De-Interleaver] (Reconstruct original temporal sequence)
                |
                v
[Adaptive Jitter Buffer (RFC 3550)] (Dynamic 40ms–150ms Delay)
                |
                v
[Block Erasure FEC Decoder] <--- (Reconstructs missing packets if erasure <= K)
                |
                | (Frame Still Missing?)
                +----------------------------+
                | No                         | Yes
                v                            v
[Audio Decompressor]           [Pitch-Synchronous Waveform PLC]
                \                            /
                 v                          v
       [Smooth Overlap-Add (OLA) Waveform Cross-Fader]
                            |
                            v
       [AudioTrack (Android) / AVAudioEngine (iOS) Playout]
```

### 2.3 Offline Vector Topographic & Trail Elevation Profile Subsystem
```
[Local Tile Storage (.mvt / SD Card)]
                |
                v
[OfflineVectorTileEngine (commonMain)] <--- [Convoy GPS Bounding Box]
                | (LRU Cache: 64 tiles, <15ms spatial query)
                v
[MVT Protobuf & Zig-Zag Coordinate Parser]
                | (Decoded Contours & Trail Lines)
                v
[ConvoyElevationEngine] <--- [GPS Fixes: Lat, Lon, Altitude, Accuracy]
                |
                +---> [Grade % Math: (delta_alt / delta_dist) * 100]
                |        |
                |        +---> [> 8%: Caution (Orange) / > 12%: Extreme (Red)]
                |
                +---> [Convoy Lead/Sweep Delta-h Math & Summit Detection]
                |
                v
[IntercomBridge / KMP Flow]
        |                                       |
        v                                       v
[Jetpack Compose Cockpit ELEV Tab]      [100% Native SwiftUI Cockpit HUD]
(Android / Desktop)                     (iOS Native Canvas)
```

---

## 3. System State Machines

### 3.1 Multi-Path Transport Failover State Machine
```
                       +-------------------+
                       |    BLE_PRIMARY    |  (Q_link >= 70, BLE Loss < 10%)
                       +-------------------+
                             |       ^
            (Q_link < 70)    |       |  (Q_link >= 85 for 5 sec)
                             v       |
                   +---------------------------+
                   |     DEGRADED_HEDGING      |  (Dual-stream BLE + Cellular)
                   +---------------------------+
                             |       ^
       (BLE Loss >= 35%)     |       |  (BLE Loss < 25%)
                             v       |
                       +-------------------+
                       |  CELLULAR_ACTIVE  |  (100% SFU Relay Mode)
                       +-------------------+
                             |
                             |  (BLE Beacons Detected)
                             v
                       +-------------------+
                       |    RECOVERING     |  (Hysteresis validation period)
                       +-------------------+
```

### 3.2 Adaptive Multi-PHY & Congestion State Machine
```
                       +-------------------+
                       |      PHY_LE_2M    |  (High Throughput: RSSI >= -65 dBm, BER < 1%)
                       +-------------------+
                             |       ^
         (RSSI < -70 dBm)    |       |  (RSSI >= -60 dBm, Stable 10s)
                             v       |
                       +-------------------+
                       |      PHY_LE_1M    |  (Standard Range: -70 dBm <= RSSI <= -85 dBm)
                       +-------------------+
                             |       ^
         (RSSI < -85 dBm)    |       |  (RSSI >= -75 dBm, Stable 10s)
                             v       |
                       +-------------------+
                       |   PHY_LE_CODED    |  (Long Range S8: RSSI < -85 dBm, Canyon Reach)
                       +-------------------+
```
