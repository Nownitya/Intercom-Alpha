# Security Policy — Intercom-Alpha

Intercom-Alpha is designed for off-grid peer-to-peer voice communication. Security, confidentiality, and integrity of audio frames and group signaling are central to the system architecture.

---

## 🛡️ Supported Versions

| Version | Supported | Notes |
| :--- | :--- | :--- |
| `0.6.x` (Sprint 06 / Active) | ✅ Yes | Active development |
| `0.5.x` | ✅ Yes | Production-grade ChaCha20-Poly1305 AEAD |
| `< 0.5.0` | ❌ No | Historical unencrypted prototype builds |

---

## 🔒 Cryptographic Architecture

* **AEAD Cipher**: Pure Kotlin implementation of RFC 8439 ChaCha20-Poly1305 (`app/sharedLogic/src/commonMain/.../PacketCipher.kt`).
* **Key Derivation**: SHA-256 HKDF over pre-shared group secrets exchanged via offline `INTERCOM:v1:` QR codes.
* **Replay & Storm Protection**: 5,000ms sliding-window cache in `PacketDeduplicator.kt` to prevent broadcast storms, packet replaying, and flooding loops.

---

## 🚨 Reporting a Vulnerability

If you discover a security vulnerability, cryptographic weakness, or replay defect in Intercom-Alpha:

1. **Do NOT open a public GitHub issue.**
2. Send an email describing the vulnerability, proof of concept, and affected versions to the repository maintainer.
3. You should expect an acknowledgment within 48 hours and a coordinated disclosure timeline.
