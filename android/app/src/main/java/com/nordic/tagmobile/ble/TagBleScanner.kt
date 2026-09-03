package com.nordic.tagmobile.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.ParcelUuid
import com.nordic.tagmobile.debug.AgentDebugLog
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanRecord
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

    private val appContext = context.applicationContext
    private val scanner = BluetoothLeScannerCompat.getScanner()
    private var scanning = false
    private var debugSamples = 0

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val record = result.scanRecord
            val advName = record?.deviceName
            val cacheName = try {
                device.name
            } catch (_: SecurityException) {
                null
            }
            // Current production resolve path (unchanged for measurement)
            val name = advName ?: cacheName ?: "Unknown"
            val parsedAdName = parseLocalNameFromBytes(record)

            // #region agent log
            if (debugSamples < 50) {
                debugSamples++
                AgentDebugLog.init(appContext)
                AgentDebugLog.log(
                    hypothesisId = "B_C_D",
                    location = "TagBleScanner.onScanResult",
                    message = "scan_hit",
                    data = mapOf(
                        "address" to device.address,
                        "rssi" to result.rssi,
                        "advName" to (advName ?: ""),
                        "cacheName" to (cacheName ?: ""),
                        "parsedAdName" to (parsedAdName ?: ""),
                        "resolved" to name,
                        "isLegacy" to result.isLegacy,
                        "bytesLen" to (record?.bytes?.size ?: -1),
                    ),
                )
            }
            // #endregion

            listener?.onDevice(device, result.rssi, name)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            // #region agent log
            AgentDebugLog.init(appContext)
            AgentDebugLog.log(
                hypothesisId = "C",
                location = "TagBleScanner.onScanFailed",
                message = "scan_failed",
                data = mapOf("errorCode" to errorCode),
            )
            // #endregion
            listener?.onError("BLE scan failed: $errorCode")
        }
    }

    fun start() {
        if (scanning) return
        debugSamples = 0
        AgentDebugLog.init(appContext)
        // #region agent log
        AgentDebugLog.log(
            hypothesisId = "C",
            location = "TagBleScanner.start",
            message = "scan_start",
            data = mapOf(
                "legacyFalse" to true,
                "phyAll" to true,
                "debugPath" to AgentDebugLog.path(),
            ),
        )
        // #endregion
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

        /** Parse Complete (0x09) / Shortened (0x08) Local Name from AD bytes (debug + future use). */
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
