package com.example.disasterlink.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque
import java.util.UUID

object BluetoothUuids {
    // Keep these UUIDs identical in the ESP32 field-node firmware
    // (esp32/field-node/field-node.ino — NUS_SERVICE_UUID / NUS_RX_UUID / NUS_TX_UUID).
    val SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val WRITE: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")   // phone -> field node
    val NOTIFY: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")  // field node -> phone
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

/**
 * Talks to the ESP32 field node over BLE (Nordic UART Service).
 *
 * This is the "no phone-network" path: the phone never touches the internet
 * here. It writes a short command ("SOS" or "LOCATION|lat|lng") to a field
 * node's WRITE characteristic; the field node relays it over LoRa to a
 * gateway ESP32 (held by a rescue team / authority), which is the one board
 * that actually needs Wi-Fi to reach the backend. See esp32/README.md for
 * the full field-node -> gateway -> backend path.
 */
class BluetoothManager(private val context: Context) {
    companion object {
        @Volatile private var sharedInstance: BluetoothManager? = null
        fun shared(context: Context): BluetoothManager =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: BluetoothManager(context.applicationContext).also { sharedInstance = it }
            }
    }
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices
    private val _status = MutableStateFlow("Bluetooth idle")
    val status: StateFlow<String> = _status
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    // True once we've negotiated a bigger MTU AND enabled notifications —
    // i.e. actually safe to send commands, not just GATT-connected.
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val messages: SharedFlow<String> = _messages

    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    // BLE only allows ONE outstanding GATT operation (write/read/descriptor
    // write) at a time — starting a second before the first's callback fires
    // silently fails or throws. The original code had no queue, which is
    // fine for a single automatic LOCATION write, but breaks once the SOS
    // button can also fire a write while one is already in flight. This is
    // a small FIFO queue drained one write at a time from the callback.
    private val pendingWrites = ArrayDeque<ByteArray>()
    private var writeInFlight = false

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val current = _devices.value.toMutableList()
            if (current.none { it.address == result.device.address }) {
                current.add(result.device); _devices.value = current
            }
        }
        override fun onScanFailed(errorCode: Int) { _status.value = "BLE scan failed: $errorCode" }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasBluetoothPermission()) { _status.value = "Bluetooth permission required"; return }
        if (adapter?.isEnabled != true) { _status.value = "Turn on Bluetooth"; return }
        _devices.value = emptyList()
        scanner = adapter.bluetoothLeScanner
        scanner?.startScan(callback)
        _status.value = "Scanning for ESP32…"
    }

    @SuppressLint("MissingPermission")
    fun stopScan() { scanner?.stopScan(callback); _status.value = "Scan stopped" }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (!hasBluetoothPermission()) { _status.value = "Bluetooth permission required"; return }
        stopScan()
        _status.value = "Connecting to ${device.name ?: device.address}…"
        gatt?.close()
        pendingWrites.clear()
        writeInFlight = false
        _ready.value = false
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect(); gatt?.close(); gatt = null
        writeCharacteristic = null; notifyCharacteristic = null
        pendingWrites.clear(); writeInFlight = false
        _connected.value = false; _ready.value = false
        _status.value = "Disconnected"
    }

    /**
     * Queues a command for the field node: "SOS" to trigger an offline SOS
     * relay over LoRa, or "LOCATION|<lat>|<lng>" to relay a position.
     * Returns false immediately if we're not connected/ready yet — the
     * caller (UI) should check `ready` and tell the user to wait/reconnect
     * rather than silently losing the command.
     */
    @SuppressLint("MissingPermission")
    fun send(command: String): Boolean {
        if (!_ready.value || writeCharacteristic == null) return false
        pendingWrites.addLast(command.toByteArray(Charsets.UTF_8))
        pumpWriteQueue()
        return true
    }

    fun sendSos(): Boolean = send("SOS")

    /**
     * Module 3 offline SOS. The phone sends a compact identity/location packet
     * to the field ESP32. The ESP32 then signs and broadcasts it over LoRa.
     */
    fun sendSos(
        clientId: String,
        governmentId: String,
        name: String,
        phone: String,
        district: String,
        latitude: Double,
        longitude: Double,
        message: String
    ): Boolean {
        fun clean(value: String, max: Int): String = value
            .replace("|", "/")
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\"", "'")
            .trim()
            .take(max)
        val command = "SOS|${clean(clientId.replace("-", ""), 32)}|" +
            "${clean(governmentId, 20)}|${clean(name, 30)}|${clean(phone, 16)}|" +
            "${clean(district, 20)}|$latitude|$longitude|${clean(message, 40)}"
        return send(command)
    }

    fun sendLocation(latitude: Double, longitude: Double): Boolean = send("LOCATION|$latitude|$longitude")

    @SuppressLint("MissingPermission")
    private fun pumpWriteQueue() {
        if (writeInFlight) return
        val characteristic = writeCharacteristic ?: return
        val g = gatt ?: return
        val next = pendingWrites.pollFirst() ?: return
        writeInFlight = true
        val started = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(characteristic, next, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = next
            @Suppress("DEPRECATION")
            g.writeCharacteristic(characteristic)
        }
        if (!started) {
            // Couldn't even start the write (e.g. GATT busy) — drop the
            // in-flight flag so the next queued command can try, and let
            // the UI know this one didn't go out.
            writeInFlight = false
            _status.value = "Failed to send command — try again"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _status.value = "Connected — negotiating link…"
                // Default BLE MTU is 23 bytes (20 usable after the ATT
                // header). "LOCATION|11.123456|78.123456" alone is ~29
                // bytes, so without this the write can fail or truncate.
                // requestMtu() triggers onMtuChanged below; we wait for
                // that before calling discoverServices().
                g.requestMtu(185)
            } else {
                writeCharacteristic = null; notifyCharacteristic = null
                pendingWrites.clear(); writeInFlight = false
                _connected.value = false; _ready.value = false
                _status.value = "Disconnected from ESP32"
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            // Proceed either way — if negotiation failed we just fall back
            // to the 23-byte default, which still fits our short commands
            // most of the time; we don't want to strand the connection.
            _status.value = "Link ready (MTU $mtu) — discovering ESP32 services"
            g.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(BluetoothUuids.SERVICE)
            writeCharacteristic = service?.getCharacteristic(BluetoothUuids.WRITE)
            notifyCharacteristic = service?.getCharacteristic(BluetoothUuids.NOTIFY)
            _connected.value = writeCharacteristic != null

            if (notifyCharacteristic == null) {
                _ready.value = false
                _status.value = if (writeCharacteristic != null) "ESP32 found, but no status channel" else "ESP32 service not found — is it a DisasterLink field node?"
                return
            }
            g.setCharacteristicNotification(notifyCharacteristic, true)
            val descriptor = notifyCharacteristic!!.getDescriptor(BluetoothUuids.CCCD)
            if (descriptor == null) {
                // No CCCD to write to but we at least have both
                // characteristics — allow sending, just without live status.
                _ready.value = writeCharacteristic != null
                _status.value = "ESP32 ready (no notify descriptor)"
                return
            }
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
            // _ready flips true in onDescriptorWrite below, once notifications
            // are actually confirmed on — that's genuinely "ready to send".
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val ok = status == BluetoothGatt.GATT_SUCCESS && writeCharacteristic != null
            _ready.value = ok
            _status.value = if (ok) "ESP32 ready — SOS/location can be sent" else "Failed to enable ESP32 status notifications"
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeInFlight = false
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _status.value = "Command failed to send (GATT status $status) — retrying is safe"
            }
            pumpWriteQueue() // send the next queued command, if any
        }

        private fun emitNotification(value: ByteArray) {
            val message = String(value, Charsets.UTF_8)
            _messages.tryEmit(message)
            _status.value = "ESP32: $message"
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            emitNotification(characteristic.value ?: ByteArray(0))
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            emitNotification(value)
        }
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}
