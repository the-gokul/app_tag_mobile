package com.nordic.tagmobile

import com.nordic.tagmobile.model.ConnectedDevice
import com.nordic.tagmobile.model.DeviceConfig
import com.nordic.tagmobile.model.RecordingState
import com.nordic.tagmobile.protocol.SensorCsvRow

object TagSession {
    var connectedDevice: ConnectedDevice? = null
    var recordingState: RecordingState = RecordingState.IDLE
    var syncBaseUnixMs: Long = 0L
    var tagUptimeAtSync: Long? = null
    val receivedRows: MutableList<SensorCsvRow> = mutableListOf()
    var packetCount: Int = 0
    var deviceConfig: DeviceConfig = DeviceConfig.default()
    var customDataEnabled: Boolean = false
    var includeSiUnits: Boolean = false

    fun resetRecording() {
        recordingState = RecordingState.IDLE
        syncBaseUnixMs = 0L
        tagUptimeAtSync = null
        receivedRows.clear()
        packetCount = 0
    }

    fun isConnected(): Boolean = connectedDevice != null
}
