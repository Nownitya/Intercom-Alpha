# Technical Specification Document (TSD) — Intercom-Alpha

> **Document Version:** 3.0.0-beta1  
> **Status:** Detailed Engineering Protocol & Mathematical Specification  
> **Scope:** Packet Framing, Wire Protocols, Mathematical Models, and BLE GATT Service Layout  
> **Repository:** `Intercom-Alpha`  

---

## 1. Binary Packet Wire Protocol Specification

All over-the-air communication over BLE GATT characteristics and WebSocket cellular relays conforms to the binary packet structure detailed below.

### 1.1 Wire Protocol Frame Layout
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  Magic (0x49) |  Version (0x01)|  Opcode       |  Flags / TTL  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Sequence Number (32-bit)                |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Sender Node ID (32-bit)                 |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       Payload Length (16-bit) |      Adler-32 Checksum (16-bit)|
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
+                    Encrypted Payload (Variable)               +
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Poly1305 Auth Tag (16 Bytes / 128-bit)     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### 1.2 Opcode Table
| Opcode (Hex) | Name | Priority Tier | Description |
| :--- | :--- | :--- | :--- |
| `0x01` | `VOICE_FRAME` | Priority 1 | Compressed audio payload (4-bit ADPCM or Opus frame) |
| `0x02` | `FEC_PARITY_FRAME` | Priority 1 | Systematic block erasure parity frame |
| `0x03` | `EMERGENCY_SOS` | Priority 0 | High-priority eCall impact or manual SOS alert |
| `0x04` | `GPS_TELEMETRY` | Priority 3 | Delta-compressed convoy coordinate beacon |
| `0x05` | `MESH_ROUTING_CTRL`| Priority 2 | AODV-Lite RREQ / RREP / RERR or Heartbeat ping |
| `0x06` | `SNAPSHOT_CHUNK` | Priority 3 | 0.5 fps thumbnail visual snapshot data chunk |
| `0x07` | `TRAIL_GPX_CHUNK` | Priority 3 | Segmented offline route vector chunk |
| `0x08` | `REKEY_EPOCH` | Priority 2 | Forward-secret group rekeying announcement |
| `0x09` | `ATT_AGGREGATE` | Priority 1 | Multi-frame packed payload for high-MTU BLE links |
| `0x0A` | `VECTOR_TILE_REQ` | Priority 3 | Bounding box spatial vector contour request |
| `0x0B` | `ELEVATION_PROFILE`| Priority 3 | Elevation profile & slope grade % telemetry |
| `0x0C` | `CONGESTION_STATE` | Priority 2 | Local channel airtime %, neighbor density & backoff |

---

## 2. Mathematical Models & DSP Algorithms

### 2.1 Composite Link Quality Score ($Q_{link}$)
The composite link health score $Q_{link} \in [0, 100]$ is computed every 250ms via Exponentially Weighted Moving Average (EWMA):

$$Q_{link} = w_{1} \cdot S_{	ext{RSSI}} + w_{2} \cdot (1 - 	ext{Loss}_{	ext{EWMA}}) + w_{3} \cdot S_{	ext{Jitter}} + w_{4} \cdot S_{	ext{ETX}}$$

Where:
* $w_1 = 0.35$, $w_2 = 0.35$, $w_3 = 0.15$, $w_4 = 0.15$
* $S_{	ext{RSSI}} = 	ext{clamp}\left(rac{	ext{RSSI}_{	ext{EWMA}} - (-95)}{-50 - (-95)} 	imes 100, 0, 100ight)$
* $	ext{Loss}_{	ext{EWMA}} = lpha \cdot 	ext{Loss}_{	ext{curr}} + (1 - lpha) \cdot 	ext{Loss}_{	ext{prev}}$ (with $lpha = 0.2$)
* $S_{	ext{Jitter}} = 	ext{clamp}\left(100 - rac{	ext{Jitter}_{	ext{variance}}}{50	ext{ms}} 	imes 100, 0, 100ight)$

### 2.2 Forward Error Correction (Systematic Block Erasure)
For a block of $K$ source packets $P_1, P_2, \dots, P_K$, the parity packet $C$ is generated using Galois Field $GF(2^8)$ or interleaved XOR parity:

$$C = igoplus_{i=1}^{K} P_i$$

When receiver receives any $K$ packets out of $N = K + M$, any single erased packet $P_m$ is recovered instantly via:

$$P_m = C \oplus \left(igoplus_{i 
eq m} P_iight)$$

### 2.3 Pitch-Synchronous Waveform Packet Loss Concealment (PLC)
When consecutive packets are lost and cannot be recovered via FEC:
1. **Autocorrelation Pitch Estimation:** Compute autocorrelation over history buffer of length $W = 320$ samples (20ms at 16kHz):
   $$R(	au) = \sum_{n=0}^{W-1} x(n) \cdot x(n - 	au), \quad 	au \in [32, 160] \implies [100	ext{ Hz}, 500	ext{ Hz}]$$
2. **Optimal Pitch Lag:** Identify $	au^* = rg\max_{	au} R(	au)$.
3. **Progressive Attenuation Decay:** Synthesize replacement frame $s(n)$ by replicating pitch period with attenuation factor $eta = 10^{-2.5 / 20} pprox 0.75$:
   $$s(n) = eta^k \cdot x(n - 	au^*)$$
   Where $k \in \{1, 2, 3\}$ is the consecutive lost frame index. After $k > 3$, output is muted to zero.

### 2.4 Time-Diversity Matrix Block Interleaver
To convert burst packet drops from physical radio fades into isolated single-packet erasures:
1. Arrange an $M \times N$ matrix where $M$ is block depth (number of FEC groups) and $N$ is block span (packets per group).
2. Write audio and parity frames into matrix row-wise.
3. Transmit packets column-wise over BLE transport.
4. An RF fade dropping up to $M$ contiguous packets manifests as at most one missing packet per FEC group, guaranteeing $100\%$ mathematical reconstruction by the receiver's FEC decoder.

### 2.5 Convoy Elevation & Incline Mathematical Engine
1. **Haversine Distance ($d$):**
   $$a = \sin^2\left(\frac{\Delta \phi}{2}\right) + \cos(\phi_1)\cos(\phi_2)\sin^2\left(\frac{\Delta \lambda}{2}\right)$$
   $$d = 2 \cdot R \cdot \text{atan2}\left(\sqrt{a}, \sqrt{1 - a}\right)$$
2. **Slope Grade Percentage ($G$):**
   $$G = \left(\frac{\Delta h}{d}\right) \times 100$$
   * $G > 8\% \implies$ Caution (Orange)
   * $G > 12\% \implies$ Extreme (Red flashing warning)
3. **Lead/Sweep Vertical Delta ($\Delta h_{convoy}$):**
   $$\Delta h_{convoy} = |h_{lead} - h_{sweep}|$$

### 2.6 Mesh Channel Airtime & Adaptive Congestion Control
1. **Channel Occupancy Metric ($U_{air}$):**
   $$U_{air} = \frac{\sum T_{tx\_packet}}{T_{window}} \times 100\%$$
2. **Dynamic Backoff Scheduler:**
   $$T_{backoff} = T_{min} + \text{rand}(0, 2^{\min(k, 6)} - 1) \cdot T_{slot}$$
3. **Probabilistic Flooding Suppression:**
   When local neighbor density $N_{local} > 8$:
   $$P_{\text{rebroadcast}} = \min\left(1.0, \frac{K_{redundancy}}{C_{\text{received}}}\right)$$

---

## 3. Bluetooth Low Energy GATT Specification

### 3.1 Service & Characteristic UUID Map
* **Primary Intercom Service UUID:** `0000FEA0-0000-1000-8000-00805F9B34FB`
* **Characteristics:**
  * **Audio Ingestion / Rx:** `0000FEA1-0000-1000-8000-00805F9B34FB` (Properties: `WRITE_NO_RESPONSE`, `NOTIFY`)
  * **Mesh Relay Broadcast:** `0000FEA2-0000-1000-8000-00805F9B34FB` (Properties: `INDICATE`, `WRITE`)
  * **Tactile Telemetry & Heartbeat:** `0000FEA3-0000-1000-8000-00805F9B34FB` (Properties: `NOTIFY`, `READ`)
  * **Emergency SOS Channel:** `0000FEA4-0000-1000-8000-00805F9B34FB` (Properties: `INDICATE`, `WRITE`)
