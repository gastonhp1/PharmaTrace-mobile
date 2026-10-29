# PharmaTrace mobile

[English](README.md)

App Android (Kotlin, Jetpack Compose) de [Pharmatrace](https://github.com/gastonhp1/Pharmatrace), trazabilidad
de medicamentos sobre Hyperledger Fabric. Su primer trabajo es **abrir la PharmaBox**, la valija de cadena de
frío de [Pharmatrace-firmware](https://github.com/gastonhp1/Pharmatrace-firmware), solo por quien tiene permiso.

1. La caja muestra un QR `PB1:<deviceId>:<nonce>` al apretar su botón.
2. La app lo escanea y le pide un token a la API (`POST /api/box/unlock`) con la sesión del usuario.
3. El backend lee la caja y el cargamento del ledger, aplica la regla (el destinatario de un cargamento en
   tránsito, no quien lo despachó; el regulador siempre…), audita y firma un token de 106 bytes.
4. La app verifica que el token sea para esa caja y ese nonce, y lo escribe por BLE.
5. La caja verifica la firma sin internet, abre y notifica el resultado.

La regla vive en un solo lugar, el backend:
[`iot/docs/BOX_UNLOCK.md`](https://github.com/gastonhp1/Pharmatrace/blob/main/iot/docs/BOX_UNLOCK.md).

## Pantallas

Ingreso (servidor, usuario y contraseña de la organización), inicio, escaneo (solo acepta QRs de PharmaBox),
apertura paso a paso, y cargamento: custodia, lotes y cadena de frío (veredicto, tiempo fuera de rango contra el
presupuesto, última lectura, eventos anclados).

## Decisiones

- **La app nunca tiene claves de Fabric**: usa la API custodial con una sesión por usuario, guardada cifrada con
  una clave AES-GCM del Android Keystore y excluida de los backups.
- **El token se revisa antes de mandarlo**: cada escritura rechazada gasta uno de los tres intentos de la caja.
- **Operaciones BLE en serie y con timeout**; pide MTU 185 y si no, Android hace escritura larga.
- **La caja correcta**: prefiere la que anuncia el `deviceId` como nombre; si no hay nombre, lo confirma con el
  GAP Device Name antes de escribir.
- **`core/` es Kotlin puro**, testeado en la JVM contra un token firmado por el backend real.

## Build

```bash
./gradlew :core:test :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

El build debug permite HTTP (backend en la LAN) y apunta por defecto a `http://10.0.2.2:3001` (la PC vista desde
el emulador). El release es solo HTTPS. El CI corre tests y lint y publica el APK debug como artefacto.

## Estado

La lógica del protocolo y el cliente de la API están testeados. **BLE contra una PharmaBox real y el escaneo con
cámara no se probaron en un dispositivo** (el firmware de la caja tampoco se probó en hardware todavía).
