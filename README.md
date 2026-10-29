# PharmaTrace mobile

[Español](README.es.md)

Android app (Kotlin, Jetpack Compose) for [Pharmatrace](https://github.com/gastonhp1/Pharmatrace), drug
traceability on Hyperledger Fabric. Its first job is **opening the PharmaBox**, the cold-chain case built in
[Pharmatrace-firmware](https://github.com/gastonhp1/Pharmatrace-firmware), from the person who is allowed to
open it and nobody else.

```
 PharmaBox                       This app                           Pharmatrace API
   button → nonce, e-paper QR       │                                   │
   "PB1:<deviceId>:<nonce>"  ◄── scan (CameraX + ML Kit)                │
                                    │── POST /api/box/unlock ──────────►│ who is it, which org?
                                    │   Authorization: Bearer           │ box + cargo from the ledger
                                    │◄────────── 106-byte token ────────│ policy, audit, P-256 signature
                                    │ checks box + nonce locally        │
   ◄─────── BLE write `unlock` ─────│                                   │
   verifies offline, opens          │                                   │
   ─────── BLE notify `status` ────►│ shows the result                  │
```

The box needs no internet and no user keys: only the backend's public key. The rule of who can open
(the recipient of a cargo in transit, not the sender; the regulator always…) lives in one place, the backend
([`iot/docs/BOX_UNLOCK.md`](https://github.com/gastonhp1/Pharmatrace/blob/main/iot/docs/BOX_UNLOCK.md)).

## Screens

| | |
| --- | --- |
| **Login** | Server URL, user and password of your org (users are created on the API with `scripts/user.js`) |
| **Home** | Open a PharmaBox · look up a cargo |
| **Scan** | Reads only PharmaBox QRs; anything else is ignored |
| **Unlock** | Step by step: authorization → find the box → connect → send → answer of the box. A denial from the server says why and sends nothing to the box |
| **Cargo** | Custody (state, owner, destination, batches) and cold chain: verdict, time out of range against the budget, last reading, events anchored in the ledger |

## Design decisions

- **The app never holds Fabric keys.** It talks to the custodial REST API with a per-user session (HS256, 8 h)
  stored encrypted with an Android Keystore AES-GCM key, excluded from backups.
- **A token is checked before it is sent.** Every rejected write costs one of the box's three attempts per QR,
  so the app verifies length, header, lifetime, box (`SHA-256(deviceId)[:8]`) and nonce against the QR it scanned.
  The signature is the box's job.
- **BLE operations are serialized and time-boxed** (Android allows one GATT operation in flight). MTU 185 is
  requested; if refused, Android falls back to a long write.
- **Finding the right box.** The box advertises its service only while its QR is valid. The app prefers the
  one advertising the `deviceId` as its name; if names are not advertised it confirms with the GAP Device Name
  after connecting, before writing anything.
- **`core/` is plain Kotlin**: QR parsing, token checks and BLE result codes, tested on the JVM against a token
  signed by the real backend.

## Build

Android Studio Ladybug or newer, or the command line with JDK 17:

```bash
./gradlew :core:test :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug                  # app/build/outputs/apk/debug/
```

The debug build allows plain HTTP (to a backend on the LAN, `npm start` in `Pharmatrace/backend`) and
defaults to `http://10.0.2.2:3001`, the host seen from the emulator. The release build is HTTPS-only.
CI ([`android.yml`](.github/workflows/android.yml)) runs the tests and lint and publishes the debug APK as an
artifact.

To try it end to end, on the backend:

```bash
cd Pharmatrace/backend
node scripts/unlock-key.js generate data/unlock-key.pem   # also prints include/unlock_key.h for the box
node scripts/user.js add dario DistMSP
# .env: SESSION_SECRET, UNLOCK_KEY_FILE=data/unlock-key.pem
```

## Status

| | |
| --- | --- |
| Protocol logic (`core/`) | Tested on the JVM, including a token signed by the backend |
| API client | Tested against MockWebServer |
| BLE against a real PharmaBox | **Not tested**: the box firmware is not tested on hardware yet either |
| Camera scanning | **Not tested on a device** |

## Next

- Batch operations from the phone (dispatch, receive, dispense) once the API moves its writes from the shared
  API key to per-user sessions.
- Scanning the GS1 DataMatrix of a medicine box to jump to its batch.
- Push notifications when a cargo leaves its range.
