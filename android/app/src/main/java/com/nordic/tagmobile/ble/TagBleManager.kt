package com.nordic.tagmobile.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.nordic.tagmobile.protocol.TagCommand
import com.nordic.tagmobile.protocol.TagUuids
import java.util.UUID

class TagBleManager(context: Context) {

    interface Listener {
        fun onScanResult(device: BluetoothDevice, rssi: Int, displayName: String) {}
        fun onConnected(device: BluetoothDevice) {}
        fun onDisconnected() {}
        fun onPacketReceived(data: ByteArray) {}
        fun onError(message: String) {}
    }

    var listener: Listener? = null

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter

    private var gatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var syncBaseUnixMs: Long = 0L
    private var pendingConnectDevice: BluetoothDevice? = null

    private val streamServiceUuid = UUID.fromString(TagUuids.STREAM_SERVICE)
    private val sensorDataUuid = UUID.fromString(TagUuids.SENSOR_DATA)
    private val commandUuid = UUID.fromString(TagUuids.COMMAND)
    private val cccUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val isConnected: Boolean get() = gatt != null && commandChar != null

    private val streamServiceParcel = ParcelUuid(streamServiceUuid)

    private fun isTagAdvertisement(result: ScanResult): Boolean {
        val name = result.scanRecord?.deviceName ?: result.device.name
        if (!name.isNullOrBlank() && name.startsWith("Tag", ignoreCase = true)) {
            return true
        }
        val uuids = result.scanRecord?.serviceUuids ?: return false
        return uuids.any { it == streamServiceParcel }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            if (!isTagAdvertisement(result)) return
            val displayName = result.scanRecord?.deviceName
                ?: result.device.name
                ?: "Tag"
            listener?.onScanResult(device, result.rssi, displayName)
        }

        override fun onScanFailed(errorCode: Int) {
            listener?.onError("BLE scan failed: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestMtu(247)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                listener?.onDisconnected()
                closeGattOnly()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener?.onError("Service discovery failed")
                return
            }
            val service = gatt.getService(streamServiceUuid) ?: run {
                listener?.onError("TAG_STREAM service not found")
                return
            }
            notifyChar = service.getCharacteristic(sensorDataUuid)
            commandChar = service.getCharacteristic(commandUuid)
            if (notifyChar == null || commandChar == null) {
                listener?.onError("Missing GATT characteristics")
                return
            }
            enableNotifications(gatt, notifyChar!!)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener?.onConnected(gatt.device)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val data = characteristic.value ?: return
            listener?.onPacketReceived(data)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            listener?.onPacketReceived(value)
        }
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: run {
            listener?.onError("Bluetooth scanner unavailable")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        stopScan()
        closeGattOnly()
        pendingConnectDevice = device
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(appContext, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        closeGattOnly()
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        syncBaseUnixMs = System.currentTimeMillis()
        writeCommand(TagCommand.startPayload(syncBaseUnixMs))
    }

    @SuppressLint("MissingPermission")
    fun stopRecording() {
        writeCommand(TagCommand.stopPayload())
    }

    fun syncBaseTimeMs(): Long = syncBaseUnixMs

    @SuppressLint("MissingPermission")
    private fun writeCommand(payload: ByteArray) {
        val char = commandChar ?: run {
            listener?.onError("Not connected")
            return
        }
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        char.value = payload
        if (gatt?.writeCharacteristic(char) != true) {
            listener?.onError("Command write failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val ccc = characteristic.getDescriptor(cccUuid) ?: return
        ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(ccc)
    }

    @SuppressLint("MissingPermission")
    private fun closeGattOnly() {
        gatt?.close()
        gatt = null
        commandChar = null
        notifyChar = null
        pendingConnectDevice = null
    }
}
