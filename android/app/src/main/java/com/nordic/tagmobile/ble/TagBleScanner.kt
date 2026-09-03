package com.nordic.tagmobile.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanRecord
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings

/**
 * Scans ALL nearby BLE advertisements (no name/UUID filter), like nRF Connect scanner.
 * Connect is validated later via TAG_STREAM GATT — not at scan filter time.
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
            val name = resolveName(device, result.scanRecord)
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
        // Prefer legacy+extended: setLegacy(false) still reports legacy PDUs on Oreo+,
        // and matches Nordic Scanner Compat extended path used by toolbox-style apps.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .setLegacy(false)
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            .setUseHardwareBatchingIfSupported(false)
            .build()
        try {
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

    @SuppressLint("MissingPermission")
    private fun resolveName(device: BluetoothDevice, record: ScanRecord?): String {
        val adv = record?.deviceName?.trim()?.takeIf { it.isNotEmpty() }
        val parsed = parseLocalNameFromBytes(record)
        val cache = try {
            device.name?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: SecurityException) {
            null
        }
        val alias = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                device.alias?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
        }
        val bonded = bondedName(device.address)
        return firstNonBlank(adv, parsed, cache, alias, bonded) ?: "Unknown"
    }

    @SuppressLint("MissingPermission")
    private fun bondedName(address: String): String? {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
            adapter.bondedDevices?.firstOrNull { it.address.equals(address, ignoreCase = true) }
                ?.name
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun hasTagServiceUuid(result: ScanResult): Boolean {
            val target = ParcelUuid.fromString(
                com.nordic.tagmobile.protocol.TagUuids.STREAM_SERVICE,
            )
            return result.scanRecord?.serviceUuids?.any { it == target } == true
        }

        private fun firstNonBlank(vararg values: String?): String? =
            values.firstOrNull { !it.isNullOrBlank() }

        /** Complete (0x09) / Shortened (0x08) Local Name from raw AD. */
        fun parseLocalNameFromBytes(record: ScanRecord?): String? {
            val bytes = record?.bytes ?: return null
            var i = 0
            while (i < bytes.size) {
                val len = bytes[i].toInt() and 0xFF
                if (len == 0) break
                if (i + len >= bytes.size) break
                val type = bytes[i + 1].toInt() and 0xFF
                if (type == 0x08 || type == 0x09) {
                    val start = i + 2
                    val end = i + 1 + len
                    if (start < end && end <= bytes.size) {
                        val name = String(bytes, start, end - start, Charsets.UTF_8).trim()
                        if (name.isNotEmpty()) return name
                    }
                }
                i += len + 1
            }
            return null
        }
    }
}
