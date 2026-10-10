# Security Policy — Intercom-Alpha

Intercom-Alpha is designed for off-grid peer-to-peer voice communication. Security, confidentiality, and integrity of audio frames and group signaling are central to the system architecture.

---

## 🛡️ Supported Versions

| Version | Supported | Notes |
| :--- | :--- | :--- |
| `2.12.x` (Sprint 25 / Active) | ✅ Yes | Multi-path failover, stream hedging, pure KMP ChaCha20-Poly1305 |
| `2.11.x` (Sprint 24) | ✅ Yes | Airtime QoS traffic shaping, emergency preemption |
| `2.10.x` (Sprint 23) | ✅ Yes | Multi-group inter-convoy federation & tenant isolation |
| `2.0.x` – `2.9.x` | ✅ Yes | Directed AODV-Lite routing, eCall SOS, vector maps, key ratcheting |
| `1.0.x` | ✅ Yes | Production baseline release with ChaCha20-Poly1305 |
| `< 1.0.0` | ❌ No | Historical pre-release builds |

---

## 🔐 Cryptographic Architecture

* **AEAD Cipher**: Pure Kotlin implementation of RFC 8439 ChaCha20-Poly1305 (`app/sharedLogic/src/commonMain/.../PacketCipher.kt`) running with five 26-bit limbs over 64-bit Longs with zero Java (`java.security.*`) or C/JNI dependencies.
* **Key Derivation**: SHA-256 HKDF over pre-shared group secrets exchanged via offline `INTERCOM:v1:` QR codes.
* **Forward-Secret Key Ratcheting**: Session keys dynamically re-ratchet upon group membership changes (`KeyRatchet.kt`).
* **Multi-Tenant Group Isolation**: Distinct cryptographic groups (Squad A vs Squad B) are encrypted under isolated keys; gateway bridges re-encrypt frames at boundary crossings (`MeshFederationGateway.kt`).
* **Replay & Storm Protection**: 5,000ms sliding-window cache in `PacketDeduplicator.kt` to prevent broadcast storms, packet replaying, and flooding loops.
* **Memory Zeroization**: Cryptographic keys are explicitly scrubbed and zeroized upon session termination.

---

## 🚨 Reporting a Vulnerability

If you discover a security vulnerability, cryptographic weakness, or replay defect in Intercom-Alpha:

1. **Do NOT open a public GitHub issue.**
2. Send an email describing the vulnerability, proof of concept, and affected versions to the repository maintainer.
