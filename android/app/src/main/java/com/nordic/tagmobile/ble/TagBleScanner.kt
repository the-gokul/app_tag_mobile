package com.nordic.tagmobile.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.ParcelUuid
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings

/**
 * Scans ALL nearby BLE advertisements (no name/UUID filter).
 * Uses Nordic Scanner Compat with extended/Coded PHY when supported.
 */
class TagBleScanner(context: Context) {

    interface Listener {
        fun onDevice(device: BluetoothDevice, rssi: Int, name: String)
        fun onError(message: String)
    }

    var listener: Listener? = null

    private val scanner = BluetoothLeScannerCompat.getScanner()
    private var scanning = false

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = result.scanRecord?.deviceName
                ?: device.name
                ?: "Unknown"
            listener?.onDevice(device, result.rssi, name)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            listener?.onError("BLE scan failed: $errorCode")
        }
    }

    fun start() {
        if (scanning) return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .setLegacy(false)
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            .setUseHardwareBatchingIfSupported(false)
            .build()
        try {
            // null filters = all BLE devices
            scanner.startScan(null, settings, callback)
            scanning = true
        } catch (e: Exception) {
            listener?.onError("Scan start failed: ${e.message}")
        }
    }

    fun stop() {
        if (!scanning) return
        try {
            scanner.stopScan(callback)
        } catch (_: Exception) {
        }
        scanning = false
    }

    companion object {
        fun hasTagServiceUuid(result: ScanResult): Boolean {
            val target = ParcelUuid.fromString(
                com.nordic.tagmobile.protocol.TagUuids.STREAM_SERVICE,
            )
            return result.scanRecord?.serviceUuids?.any { it == target } == true
        }
    }
}
