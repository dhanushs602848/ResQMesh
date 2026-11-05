# ResQMesh Secure Mesh Encryption Design & Readiness Specification

> [!IMPORTANT]
> **Status: Design, Invariant Readiness & Relay Authentication Hardening (Phase 3.5)**
> This phase establishes cryptographic envelope specifications, threat models, relay authentication, and isolated codec foundations.
> **Encryption is NOT activated on live Bluetooth Low Energy (BLE) packet transmission in this phase.**
> The existing production BLE GATT dispatcher, multi-hop relay engine, and packet formats continue to operate unencrypted.

---

## 1. Executive Architecture & Envelope Data Flow

In the ResQMesh multi-hop emergency mesh network, packets originate at a distress node (Node A), propagate across zero or more untrusted intermediate peer nodes (Node B), and terminate at rescue coordinator responder nodes (Node C).

To support multi-hop flooding without granting intermediate nodes unauthorized access to confidential incident details (e.g., patient identity, medical triage notes, precise shelter coordinates), the protocol partitions packet data into distinct architectural domains:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                ResQMesh Wire Envelope                                  │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ 1. Mutable Hop-by-Hop Relay Block (UNENCRYPTED, MUTABLE, HOP-AUTHENTICATED)           │
│    - senderNodeId: String (Transmitter of immediate hop, e.g. "RQM-B4C1")              │
│    - hopCount: Short (Incremented by each forwarding relay)                            │
│    - maxHops: Short (Flooding lifetime bound, e.g. 7)                                  │
│    - relayAuthToken: String? (Optional HMAC/signature authenticating this hop)         │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ 2. Authenticated End-to-End Metadata Block (UNENCRYPTED, CRYPTOGRAPHICALLY BOUND TO AAD)│
│    - version: Byte (Envelope protocol version, currently 1)                            │
│    - messageId: String (UUID v4 format, freshness & anti-replay identifier)            │
│    - originNodeId: String (Original emergency author node ID, e.g. "RQM-A1B2")         │
│    - keyId: String (Key identifier matching provisioned Keystore alias)               │
│    - timestamp: Long (Creation epoch milliseconds)                                     │
│    - priority: Byte (Incident priority level: LOW, MEDIUM, HIGH, CRITICAL)             │
│    - payloadType: Byte (Message classification: SOS, ACK, TELEMETRY)                   │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ 3. Cryptographic AEAD Payload Block                                                    │
│    - nonce: 12-byte random IV (Unique per message, cryptographically generated)       │
│    - ciphertext: AES-256-GCM encrypted payload (Contains title, details, GPS coords)   │
│    - tag: 16-byte GCM authentication tag (Authenticates Ciphertext + AAD Header)       │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Relaying Without Decryption

Intermediate relay nodes inspect only the **Mutable Relay Block** and routing IDs:
1. Verify `hopCount < maxHops` and `hopCount >= 0`.
2. Check `isAlreadyRelayed(messageId)` against local in-memory sliding window cache.
3. Increment `hopCount = hopCount + 1`.
4. Overwrite `senderNodeId = localNodeId`.
5. Optionally sign hop transition via `RelayAuthenticator.signRelayHop()`.
6. Forward packet over BLE GATT to adjacent peers.

**Crucial Invariant:** Intermediate relays do not decrypt the ciphertext, do not check the GCM tag, and do not require possession of the group key.

---

## 2. Field Authentication & Security Classification

| Field | Location | Encryption | Authentication Scope | Authenticated By | Mutable in Transit? |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `version` | Envelope AAD | Plaintext | End-to-End | AES-GCM AAD | No |
| `messageId` | Envelope AAD | Plaintext | End-to-End | AES-GCM AAD | No |
| `originNodeId` | Envelope AAD | Plaintext | End-to-End | AES-GCM AAD (Group Key) | No |
| `keyId` | Envelope AAD | Plaintext | End-to-End | AES-GCM AAD | No |
| `timestamp` | Envelope AAD | Plaintext | End-to-End | AES-GCM AAD | No |
| `priority` | Envelope AAD | Plaintext | End-to-End | AES-GCM AAD | No |
| `payloadType` | Envelope AAD | Plaintext | End-to-End | AES-GCM AAD | No |
| `title` | Inner Payload | AES-256-GCM | End-to-End | AES-GCM Tag | No |
| `content` | Inner Payload | AES-256-GCM | End-to-End | AES-GCM Tag | No |
| `latitude` | Inner Payload | AES-256-GCM | End-to-End | AES-GCM Tag | No |
| `longitude` | Inner Payload | AES-256-GCM | End-to-End | AES-GCM Tag | No |
| `senderNodeId`| Relay Header | Plaintext | Hop-by-Hop | `RelayAuthenticator` | **Yes (per hop)** |
| `hopCount` | Relay Header | Plaintext | Hop-by-Hop | `RelayAuthenticator` | **Yes (per hop)** |
| `maxHops` | Relay Header | Plaintext | Hop-by-Hop | Bounds checked | No (Fixed TTL) |
| `relayAuthToken`| Relay Header | Plaintext | Hop-by-Hop | `RelayAuthenticator` | **Yes (per hop)** |
| `nonce` (IV) | Wire Header | Plaintext | Implicit | AEAD Decryption check | No |

---

## 3. Relay Metadata Threat Analysis & Mitigations

### 1. `senderNodeId` Spoofing
- **Threat:** A rogue relay node modifies `senderNodeId` to pretend to be another peer (e.g. framing an innocent node or altering radar topology blips).
- **Mitigation:** When `RelayAuthenticator` is enabled, the transmitter must compute `signRelayHop(senderNodeId, messageId, hopCount)`. Any downstream receiver validating the hop detects the mismatch between `senderNodeId` and the token, rejecting the envelope with `INVALID_RELAY_AUTHENTICATION`.

### 2. `hopCount` Reduction Attack
- **Threat:** An attacker resets `hopCount` to 0 or 1 on an existing envelope, causing the packet to loop infinitely through the mesh, depleting node batteries and causing denial-of-service / buffer exhaustion.
- **Mitigation:** The relay authentication token cryptographically binds the specific `hopCount`. If an attacker lowers `hopCount`, verification fails (`INVALID_RELAY_AUTHENTICATION`). Furthermore, duplicate message caches prevent reprocessing the same `messageId`.

### 3. `hopCount` Overflow / Underflow Attack
- **Threat:** An attacker supplies negative numbers or values exceeding protocol limits to crash deserializers or corrupt routing loops.
- **Mitigation:** Strict boundary validation enforces `0 <= hopCount <= maxHops <= MAX_HOPS_LIMIT (32)`. Any envelope violating this is rejected with `INVALID_HOP_COUNT`.

### 4. Relay Replay Attacks
- **Threat:** An attacker captures a valid forwarded envelope and rebroadcasts it minutes or hours later.
- **Mitigation:** Two-tier protection:
  1. High-speed in-memory duplicate cache (`LinkedHashMap`, max 1,000 entries) rejects duplicates immediately with `DUPLICATE_MESSAGE`.
  2. Timestamp freshness window ($\pm 10$ minutes) rejects stale replays with `EXPIRED_MESSAGE`.

---

## 4. Group-Key Limitations & Honest Evaluation

> [!WARNING]
> **Symmetric Group Key Limitation Notice**
> AES-256-GCM uses a **symmetric group key**. All authorized responder devices hold the identical 32-byte secret.
>
> 1. **Group vs. Individual Authentication:**
>    Possession of the group key proves that a packet was authored by **someone in the authorized responder group**. It **DOES NOT prove which individual device authored it**.
> 2. **Impersonation by Group Members:**
>    Any compromised responder device holding the group key can encrypt a packet placing any arbitrary `originNodeId` into the AAD. The GCM authentication tag will succeed.
> 3. **Non-Repudiation:**
>    Symmetric encryption cannot provide cryptographic non-repudiation. For true individual sender identity authentication, a future phase must layer asymmetric digital signatures (e.g., Ed25519) inside the payload.

---

## 5. Security Failure Classification

The protocol enforces granular, unambiguous rejection reasons via `SecurityFailureReason`:

| Reason | Description | Trigger Conditions |
| :--- | :--- | :--- |
| `INVALID_AUTHENTICATION_TAG` | AEAD cryptographic verification failed. | Ciphertext bit-flip, tampered AAD (`originNodeId`, `messageId`, `priority`, `timestamp`), or wrong secret key. |
| `INVALID_RELAY_AUTHENTICATION` | Hop-by-hop relay token verification failed. | `senderNodeId` spoofed, `hopCount` reduced or altered, or invalid HMAC/signature token. |
| `INVALID_HOP_COUNT` | Hop limits or bounds violated. | `hopCount < 0`, `hopCount > maxHops`, `maxHops <= 0`, or `maxHops > 32`. |
| `DUPLICATE_MESSAGE` | Message already processed within active cache. | Identical `messageId` received twice within sliding freshness window. |
| `EXPIRED_MESSAGE` | Timestamp outside valid freshness window. | $|timestamp - now| > 600{,}000\text{ ms}$ (10 minutes drift). Distinct from duplicates! |
| `UNKNOWN_KEY` | Key identifier not provisioned on local node. | Node is the final destination and requires decryption, but key is missing. |
| `UNSUPPORTED_VERSION` | Envelope protocol version mismatch. | Header version != `CURRENT_VERSION` (1). |
| `MALFORMED_ENVELOPE` | Wire data truncated or corrupted. | Magic mismatch, truncated nonce/tag, or string length overflow. |

---

## 6. Relay Authentication Interface & Implementation

### Interface Contract (`RelayAuthenticator`)
```kotlin
interface RelayAuthenticator {
    fun signRelayHop(senderNodeId: String, messageId: String, hopCount: Int): String
    fun verifyRelayHop(senderNodeId: String, messageId: String, hopCount: Int, token: String): Boolean
}
```

### Reference Implementation (`HmacRelayAuthenticator`)
- Uses `HmacSHA256` keyed with the network/mesh key.
- Computes MAC over `"$senderNodeId:$messageId:$hopCount"`.
- Emits truncated 16-byte (32-character hex) token for compact wire overhead.
- Uses `MessageDigest.isEqual` to prevent timing side-channel attacks.

---

## 7. Replay Protection & Migration Strategy

1. **Sliding Time Window Check:**
   - Packets contain an unforgeable epoch millisecond `timestamp` bound into the AAD.
   - The validator rejects any packet where $|timestamp - now| > 600{,}000\text{ ms}$ (10-minute drift window) with `EXPIRED_MESSAGE`.
2. **UUID v4 Nonce Tracking:**
   - Every packet requires a unique UUID v4 `messageId` bound into the AAD.
   - An in-memory bounded cache (`LinkedHashMap` with max size 1,000 entries) tracks seen IDs.
   - Any packet with an identical `messageId` received within the active window is dropped as `DUPLICATE_MESSAGE`.
3. **Migration Requirement & Dual-Mode Strategy:**
   - Current ResQMesh unencrypted BLE packets use timestamp IDs: `"SOS-${System.currentTimeMillis() % 10000}"` and compound ACK IDs (`ACK-SOS-XXXX-NODEID`).
   - `InMemoryReplayProtectionValidator` provides an explicit `allowLegacyFormat: Boolean = false` migration mode.
   - When `allowLegacyFormat = true`, the validator accepts both legacy alphanumeric formats and UUID v4, subjecting both to the 10-minute sliding freshness window and duplicate suppression cache.
   - When live encryption is activated, outbound packet creation will migrate to standard `UUID.randomUUID().toString()`, while inbound message ingestion retains dual-mode compatibility during transition.

---

## 8. Automated Verification Test Suite

All security invariants are verified via automated unit and simulation test suites:

- **`:core:security:testDebugUnitTest`:**
  - `EncryptedPayloadCodecTest`: AES-GCM encryption/decryption, bit-flip rejection, AAD tampering detection, key size enforcement.
  - `SecureMeshEnvelopeCodecTest`: Header serialization, wire encoding/decoding, nonce uniqueness, truncation rejection, `relayAuthToken` preservation.
  - `InMemoryReplayProtectionValidatorTest`: UUID v4 enforcement, duplicate rejection, expiration rejection, legacy format dual-mode.
  - `RelayAuthenticatorTest`: HMAC-SHA256 token generation, sender tampering rejection, hop count reduction rejection, corrupted token handling.
  - `ResponderKeyProvisionerTest`: Keystore wrapping, key storage, unwrap validation, key removal.

- **`:app:testDebugUnitTest`:**
  - `SecureRelaySimulationTest`: Full 3-node multi-hop simulation (A $\rightarrow$ B $\rightarrow$ C), ciphertext tampering rejection, origin tampering rejection, priority downgrade rejection, duplicate rejection, expired message rejection (`EXPIRED_MESSAGE`), hop count reduction rejection, hop count overflow rejection (`INVALID_HOP_COUNT`), unsupported version rejection (`UNSUPPORTED_VERSION`), unknown key rejection (`UNKNOWN_KEY`), group member origin spoofing demonstration, valid relay authentication propagation, and legacy packet compatibility.
  - `BleMtuNegotiatorTest`: Default MTU 23 fallback, ATT 3-byte header compensation across MTU spectrum (23, 64, 247, 512, 517), bounds clamping [23..517], per-peer MTU isolation, request failure and timeout recovery.
  - `SecureEnvelopeFragmentationSimulationTest`: End-to-end integration pack-to-envelope $\rightarrow$ fragment $\rightarrow$ out-of-order reassemble $\rightarrow$ decrypt verification at minimum MTU 23 and high MTU 512.

---

## 9. Phase 3.6 — BLE MTU Negotiation & Secure Envelope Fragmentation

### 1. BLE MTU Architecture
- **Bluetooth Core ATT Rules:**
  - The default ATT MTU is **23 bytes** with a fixed **3-byte ATT header** (1 byte opcode + 2 bytes attribute handle), leaving **20 bytes of usable payload** per GATT write.
  - Negotiated MTU can expand up to **517 bytes** (514 bytes usable payload).
- **[`BleMtuNegotiator`](file:///C:/Users/DHANUSH/StudioProjects/ResQMesh/app/src/main/java/com/resqmesh/app/data/ble/BleMtuNegotiator.kt):**
  - Preferred request: 512 bytes (`requestMtu(512)`).
  - Usable payload calculation: `(negotiatedMtu - 3).coerceAtLeast(20)`. Clamped to `[23..517]`.
  - Asynchronous timeout: 2,000 ms sliding window with safe fallback to 23 bytes if the peer does not respond or rejects MTU expansion.
  - Thread-safe per-peer tracking (`ConcurrentHashMap<String, Int>`).
  - Automatic eviction on peer disconnection.

### 2. Isolated Envelope Fragmentation Wire Format
A specialized fragmentation format for encrypted envelopes (`EncryptedMeshEnvelope`), completely separate from legacy `MeshPacket` chunking:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Secure Envelope Fragment ('SF')                      │
├────────────────────────────────────────────────────────────────────────┤
│ 1. Header (18 bytes fixed)                                             │
│    - magic: 2 bytes ('S', 'F' = 0x53, 0x46)                            │
│    - transferId: 4 bytes (Int32, unique transfer session identifier)   │
│    - fragmentIndex: 2 bytes (UInt16, 0-based chunk index)              │
│    - totalFragments: 2 bytes (UInt16, total chunk count)               │
│    - totalMessageLength: 4 bytes (Int32, byte length of complete msg)  │
│    - checksum: 4 bytes (UInt32 CRC32 over fragment payload)            │
├────────────────────────────────────────────────────────────────────────┤
│ 2. Payload Area (0..N bytes, dynamically sized to fit negotiated MTU)  │
└────────────────────────────────────────────────────────────────────────┘
```

- **[`SecureEnvelopeFragmenter`](file:///C:/Users/DHANUSH/StudioProjects/ResQMesh/core/security/src/main/java/com/resqmesh/security/transport/SecureEnvelopeFragmenter.kt):** Slices envelopes into chunks fitting `(negotiatedMtu - 3 - 18)` bytes. Supports down to minimum MTU 23 (2 bytes chunk payload).
- **[`SecureEnvelopeReassembler`](file:///C:/Users/DHANUSH/StudioProjects/ResQMesh/core/security/src/main/java/com/resqmesh/security/transport/SecureEnvelopeReassembler.kt):**
  - Out-of-order chunk assembly.
  - Duplicate chunk detection (ignored without corrupting state).
  - Incomplete transfer timeout eviction (default 30 seconds).
  - Concurrency capacity limits (default max 100 concurrent transfers with LRU eviction) to prevent memory exhaustion attacks.
  - Peer and transfer ID session isolation.
  - Bit-for-bit exact byte reconstruction.

---

## 10. Security Review: HMAC Relay Authenticator Audit

An audit of the Phase 3.5 [`HmacRelayAuthenticator`](file:///C:/Users/DHANUSH/StudioProjects/ResQMesh/core/security/src/main/java/com/resqmesh/security/relay/RelayAuthenticator.kt) reveals:
1. **Secret Key Source:** The HMAC key is a 256-bit symmetric secret provisioned as a mesh network relay key.
2. **Key Sharing Model:** All authorized mesh nodes share the identical symmetric relay key.
3. **Impersonation Vulnerability:** Because the secret is symmetric, any device possessing the relay key can compute valid tokens claiming `senderNodeId = "RQM-VICTIM"` for any `messageId` and `hopCount`.
4. **Context Coverage:** The HMAC input `"$senderNodeId:$messageId:$hopCount"` binds sender, message identity, and hop count. However, the inner payload and timestamp are protected by the inner AES-GCM AEAD tag, not by the relay HMAC.
5. **Architectural Implication:** The HMAC token **proves group relay key possession**, but **does NOT provide individual relay node identity non-repudiation**. True individual relay authentication requires asymmetric digital signatures (Ed25519) per hop (Phase 4).

---

## 11. Pre-Activation Checklist (Risks & Blockers Before Live BLE Encryption)

> [!CAUTION]
> **Live BLE Encryption Remains Disabled**
> The following blockers must be addressed in subsequent dedicated phases before enabling encryption on the wire:

- [ ] **Physical Multi-Device Field Test:** Execute A-to-B-to-C relay across physical Android phones over actual BLE GATT connections.
- [x] **Wire MTU & Fragmentation:** Completed in Phase 3.6 (`BleMtuNegotiator`, `SecureEnvelopeFragmenter`, `SecureEnvelopeReassembler`).
- [ ] **Out-of-Band Key Provisioning UI:** Implement an administrative QR code scanner (CameraX / MLKit) or NFC tap screen for field key distribution.
- [ ] **Dual-Magic Ingestion in GATT Server:** Integrate `SecureMeshPacketAdapter.isEncryptedEnvelopeBytes()` in `GattServerManager` to accept both unencrypted `RM` (0x52, 0x4D) packets and encrypted `RQE` (0x52, 0x51, 0x45) envelopes during transition (Phase 3.7).
- [ ] **Message ID Format Cutover:** Switch `MeshRepository.broadcastSos()` from `SOS-XXXX` to `UUID.randomUUID().toString()`.
