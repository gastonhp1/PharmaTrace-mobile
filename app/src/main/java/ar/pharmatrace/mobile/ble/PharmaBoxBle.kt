package ar.pharmatrace.mobile.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import ar.pharmatrace.core.BoxProtocol
import ar.pharmatrace.core.UnlockResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class BleException(message: String) : Exception(message)

/**
 * Talks to the PharmaBox over BLE (Pharmatrace-firmware src/ble_unlock.*):
 *
 *   service  f30e1410-…  advertised only while the box shows a valid QR
 *   unlock   e7cd3dce-…  write: the 106-byte token
 *   status   71b4af6b-…  read + notify: 1 byte, [UnlockResult]
 *
 * Every GATT operation is awaited before the next one (Android allows one in flight) and has a
 * timeout. The caller must hold the Bluetooth permissions (see ui/Permissions.kt).
 */
@SuppressLint("MissingPermission")
class PharmaBoxBle(private val context: Context) {

    enum class Step { Scanning, Connecting, Sending, Waiting }

    private val manager = context.getSystemService(BluetoothManager::class.java)

    fun isEnabled(): Boolean = manager?.adapter?.isEnabled == true

    /** Finds the box, writes the token and returns what the box answered. */
    suspend fun unlock(deviceId: String, token: ByteArray, onStep: (Step) -> Unit = {}): UnlockResult {
        require(token.size == BoxProtocol.TOKEN_SIZE) { "token must be ${BoxProtocol.TOKEN_SIZE} bytes" }
        val adapter = manager?.adapter ?: throw BleException("Este teléfono no tiene Bluetooth")
        if (!adapter.isEnabled) throw BleException("Activá el Bluetooth")

        onStep(Step.Scanning)
        val candidates = scan(deviceId)
        if (candidates.isEmpty()) {
            throw BleException("No se encontró la caja $deviceId cerca. Acercate y verificá que siga mostrando el QR.")
        }

        // Named match first; unnamed ones are confirmed by their GAP name after connecting.
        var lastError: Exception? = null
        for (candidate in candidates) {
            onStep(Step.Connecting)
            val session = GattSession(candidate.device)
            try {
                session.connect()
                if (!candidate.nameMatches && !session.confirmName(deviceId)) {
                    Log.i(TAG, "${candidate.device.address} is not $deviceId, trying the next one")
                    continue
                }
                onStep(Step.Sending)
                session.enableStatusNotifications()
                session.writeToken(token)
                onStep(Step.Waiting)
                return session.awaitResult()
            } catch (e: BleException) {
                lastError = e
            } finally {
                session.close()
            }
        }
        throw lastError as? BleException ?: BleException("No se pudo confirmar que la caja cercana sea $deviceId")
    }

    // ------------------------------------------------------------------ scan

    private class Candidate(val device: BluetoothDevice, val nameMatches: Boolean)

    /**
     * Boxes advertising the PharmaBox service. Returns as soon as one advertises [deviceId] as its
     * name; otherwise, after [SCAN_GRACE_MS], whatever was found (the firmware may not put the name
     * in the advertisement), until [SCAN_TIMEOUT_MS].
     */
    private suspend fun scan(deviceId: String): List<Candidate> {
        val scanner = manager?.adapter?.bluetoothLeScanner ?: throw BleException("Activá el Bluetooth")
        val results = Channel<ScanResult>(Channel.UNLIMITED)
        var scanError: Int? = null
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                results.trySend(result)
            }

            override fun onBatchScanResults(list: MutableList<ScanResult>) {
                list.forEach { results.trySend(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                scanError = errorCode
            }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        val found = LinkedHashMap<String, Candidate>()
        scanner.startScan(listOf(filter), settings, callback)
        try {
            val start = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - start
                if (elapsed >= SCAN_TIMEOUT_MS) break
                if (elapsed >= SCAN_GRACE_MS && found.isNotEmpty()) break
                scanError?.let { throw BleException("Falló la búsqueda Bluetooth (código $it)") }
                val r = withTimeoutOrNull(250) { results.receive() } ?: continue
                val name = r.scanRecord?.deviceName ?: r.device.name
                val match = name == deviceId
                if (match) return listOf(Candidate(r.device, true))
                // A box that advertises a different name is not ours.
                if (name == null) found.putIfAbsent(r.device.address, Candidate(r.device, false))
            }
        } finally {
            runCatching { scanner.stopScan(callback) }
            results.close()
        }
        return found.values.toList()
    }

    // ------------------------------------------------------------------ GATT

    private inner class GattSession(private val device: BluetoothDevice) {
        private var gatt: BluetoothGatt? = null
        private val connected = CompletableDeferred<Unit>()
        private val mtu = CompletableDeferred<Int>()
        private val discovered = CompletableDeferred<Unit>()
        private var pendingRead = CompletableDeferred<ByteArray>()
        private var pendingWrite = CompletableDeferred<Int>()
        private var pendingDescriptor = CompletableDeferred<Int>()
        private val result = CompletableDeferred<Int>()
        private val disconnected = CompletableDeferred<Int>()

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    connected.complete(Unit)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    val e = BleException("Se cortó la conexión con la caja (estado $status)")
                    connected.completeExceptionally(e)
                    discovered.completeExceptionally(e)
                    pendingRead.completeExceptionally(e)
                    pendingWrite.completeExceptionally(e)
                    pendingDescriptor.completeExceptionally(e)
                    disconnected.complete(status)
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, value: Int, status: Int) {
                mtu.complete(if (status == BluetoothGatt.GATT_SUCCESS) value else DEFAULT_MTU)
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) discovered.complete(Unit)
                else discovered.completeExceptionally(BleException("No se pudieron leer los servicios de la caja ($status)"))
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                pendingWrite.complete(status)
            }

            override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
                pendingDescriptor.complete(status)
            }

            // API 33+
            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) pendingRead.complete(value)
                else pendingRead.completeExceptionally(BleException("No se pudo leer de la caja ($status)"))
            }

            @Deprecated("API < 33")
            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                onCharacteristicRead(g, c, c.value ?: ByteArray(0), status)
            }

            // API 33+
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
                if (c.uuid == STATUS && value.isNotEmpty()) result.complete(value[0].toInt() and 0xFF)
            }

            @Deprecated("API < 33")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                onCharacteristicChanged(g, c, c.value ?: ByteArray(0))
            }
        }

        suspend fun connect() {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                ?: throw BleException("No se pudo iniciar la conexión con la caja")
            step("conectar con la caja", CONNECT_TIMEOUT_MS) { connected.await() }

            // The token is 106 bytes: ask for a larger MTU. If refused, Android falls back to a
            // long (prepared) write, which NimBLE also accepts.
            val g = gatt!!
            if (g.requestMtu(DESIRED_MTU)) {
                withTimeoutOrNull(MTU_TIMEOUT_MS) { mtu.await() }
            }
            if (!g.discoverServices()) throw BleException("No se pudieron leer los servicios de la caja")
            step("leer los servicios de la caja", DISCOVERY_TIMEOUT_MS) { discovered.await() }
            if (g.getService(SERVICE) == null) throw BleException("El dispositivo no es una PharmaBox")
        }

        /** Reads the GAP Device Name (0x2A00), which the firmware sets to its deviceId. */
        suspend fun confirmName(deviceId: String): Boolean {
            val c = gatt?.getService(GAP_SERVICE)?.getCharacteristic(GAP_DEVICE_NAME) ?: return false
            return runCatching { String(read(c), Charsets.UTF_8).trimEnd('\u0000') == deviceId }.getOrDefault(false)
        }

        @Suppress("DEPRECATION")
        suspend fun enableStatusNotifications() {
            val g = gatt ?: throw BleException("Sin conexión")
            val status = g.getService(SERVICE)?.getCharacteristic(STATUS) ?: throw BleException("La caja no expone el estado de apertura")
            g.setCharacteristicNotification(status, true)
            val cccd = status.getDescriptor(CCCD) ?: return // without CCCD we fall back to reading
            pendingDescriptor = CompletableDeferred()
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            } else {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(cccd)
            }
            if (started) withTimeoutOrNull(OP_TIMEOUT_MS) { pendingDescriptor.await() }
        }

        @Suppress("DEPRECATION")
        suspend fun writeToken(token: ByteArray) {
            val g = gatt ?: throw BleException("Sin conexión")
            val c = g.getService(SERVICE)?.getCharacteristic(UNLOCK) ?: throw BleException("La caja no expone la apertura")
            pendingWrite = CompletableDeferred()
            val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT // with response: we want the ack
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, token, type) == BluetoothStatusCodes.SUCCESS
            } else {
                c.writeType = type
                c.value = token
                g.writeCharacteristic(c)
            }
            if (!started) throw BleException("No se pudo enviar el token a la caja")
            val status = step("enviar el token", OP_TIMEOUT_MS) { pendingWrite.await() }
            if (status != BluetoothGatt.GATT_SUCCESS) throw BleException("La caja no aceptó la escritura ($status)")
        }

        /** The notification from the box; if it never comes, the value of `status`. */
        suspend fun awaitResult(): UnlockResult {
            val code = withTimeoutOrNull(RESULT_TIMEOUT_MS) { result.await() }
                ?: run {
                    val c = gatt?.getService(SERVICE)?.getCharacteristic(STATUS) ?: throw BleException("Sin respuesta de la caja")
                    read(c).firstOrNull()?.toInt()?.and(0xFF) ?: throw BleException("Sin respuesta de la caja")
                }
            return UnlockResult.fromCode(code) ?: throw BleException("Respuesta desconocida de la caja ($code)")
        }

        private suspend fun read(c: BluetoothGattCharacteristic): ByteArray {
            val g = gatt ?: throw BleException("Sin conexión")
            pendingRead = CompletableDeferred()
            if (!g.readCharacteristic(c)) throw BleException("No se pudo leer de la caja")
            return step("leer de la caja", OP_TIMEOUT_MS) { pendingRead.await() }
        }

        fun close() {
            val g = gatt ?: return
            gatt = null
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }

        private suspend fun <T> step(what: String, timeoutMs: Long, block: suspend () -> T): T = try {
            withTimeout(timeoutMs) { block() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw BleException("Se agotó el tiempo al $what")
        }
    }

    companion object {
        private const val TAG = "PharmaBoxBle"
        val SERVICE: UUID = UUID.fromString(BoxProtocol.SERVICE_UUID)
        val UNLOCK: UUID = UUID.fromString(BoxProtocol.UNLOCK_CHAR_UUID)
        val STATUS: UUID = UUID.fromString(BoxProtocol.STATUS_CHAR_UUID)
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val GAP_SERVICE: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        private val GAP_DEVICE_NAME: UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")

        private const val DESIRED_MTU = 185
        private const val DEFAULT_MTU = 23
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val SCAN_GRACE_MS = 4_000L
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val MTU_TIMEOUT_MS = 3_000L
        private const val DISCOVERY_TIMEOUT_MS = 10_000L
        private const val OP_TIMEOUT_MS = 5_000L
        private const val RESULT_TIMEOUT_MS = 8_000L
    }
}

// Kept for callers that want to wait for Bluetooth to come up after asking the user to enable it.
internal suspend fun PharmaBoxBle.awaitEnabled(timeoutMs: Long = 10_000): Boolean =
    withTimeoutOrNull(timeoutMs) {
        while (!isEnabled()) delay(250)
        true
    } ?: false
