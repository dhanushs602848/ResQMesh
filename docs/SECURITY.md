# ResQMesh security module

## Scope and threat model

`core/security` provides an optional encryption primitive for SOS and ordinary message payload bytes. It is designed to detect passive eavesdropping and in-transit modification when communicating devices possess the same responder-group key. It does not establish who sent a packet: any member holding that group key can create a valid encrypted payload.

The security module is deliberately not wired into the BLE route. Do not integrate it into `BleManager`, `BlePacket`, or `BleRoutingCoordinator` until physical A→B→C relay testing has been completed and the team explicitly approves the route change. The existing packet format and routing behavior remain unchanged.

## Encryption flow

`EncryptedPayloadCodec` accepts UTF-8 or arbitrary payload bytes and a type (`SOS` or `NORMAL`). It requests the responder key by key ID, generates a new 12-byte nonce using `SecureRandom`, and encrypts with AES-256-GCM using a 128-bit authentication tag. The header is supplied as GCM additional authenticated data, so changes to the version, type, key ID, nonce, or ciphertext length fail authentication.

Binary envelope version 1 is big-endian:

| Field | Size |
|---|---:|
| Magic `RQM` | 3 bytes |
| Version (`1`) | 1 byte |
| Message type (`SOS=1`, `NORMAL=2`) | 1 byte |
| Key ID length | 1 byte |
| Key ID (ASCII `[A-Za-z0-9._-]`) | 1–64 bytes |
| GCM nonce | 12 bytes |
| Ciphertext plus tag length | 4 bytes |
| Ciphertext plus 128-bit tag | variable, at least 16 bytes |

The parser rejects unknown versions/types, invalid IDs, inconsistent lengths, truncation, and trailing bytes. GCM authentication failures are returned as `InvalidEncryptedPayloadException`. Callers must not continue with unauthenticated plaintext.

## Key management

`ResponderKeyProvider` is the codec boundary for key retrieval. `AndroidKeystoreResponderKeyProvider` creates a per-install AES-256 wrapping key in Android Keystore and stores provisioned responder keys only as AES-GCM-wrapped values in app-private preferences. The clear group key exists in memory only while provisioning or decrypting it for use. The implementation never generates a shared group key, embeds one in source, or logs key material or plaintext.

### Provisioning demo devices

Generate one random 32-byte responder-group key once using a trusted provisioning workstation or secure key ceremony. Transfer that same value to each demo device using a private, authenticated, out-of-band channel (for example, in person via an encrypted removable medium); do not put it in Git, chat, screenshots, shell history, app assets, or logs. A trusted bootstrapper on each device should pass the bytes to `provisionResponderKey` with the same non-secret key ID. The module intentionally has no UI or network key-distribution endpoint.

Before the demo, call `KeyFingerprint.sha256(keyBytes)` on each device and compare the complete SHA-256 fingerprints over a separate trusted channel. Do not display or transmit the group key as a substitute for fingerprint verification. Re-provisioning under a key ID replaces that device's local wrapped value; rotate and securely reprovision every demo device if a group key is suspected compromised.

## Pairing and key verification

This module does not perform pairing or authenticate device identities. Devices are considered members of the same responder group only after a trusted operator provisions the same random key and verifies matching fingerprints out of band. Anyone who obtains the group key can impersonate any group member. Production deployment needs a reviewed enrollment, revocation, and rotation process.

## Security limitations

- The group key is symmetric and shared by all provisioned devices; it provides no per-device identity, non-repudiation, forward secrecy, or selective revocation.
- Replay protection, sequence numbers, expiry, and message-level authorization are not provided by this envelope.
- A compromised/rooted device or process can potentially access the key while it is in use or invoke the crypto API. Android Keystore protects the wrapping key at rest; it does not make a shared key immune to a compromised endpoint.
- Fingerprints prove equality only when compared through an authenticated out-of-band channel.
- Nonce uniqueness depends on `SecureRandom`; a fresh 96-bit nonce is generated for every call. Do not reuse nonces with a key or replace the random source in production.
- Decryption callers must keep plaintext out of logs, analytics, crash reports, and relay diagnostics. Relay code should forward opaque encrypted bytes and must never log decrypted SOS content.
- The primitive is not yet integrated with the production BLE route, and A→B→C physical relay compatibility has not been established.
