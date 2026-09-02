package com.nordic.tagmobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nordic.tagmobile.ble.TagBleManager
import com.nordic.tagmobile.databinding.ActivityDeviceBinding
import com.nordic.tagmobile.model.RecordingState
import com.nordic.tagmobile.protocol.CsvExporter
import com.nordic.tagmobile.protocol.SensorPacketParser
import com.nordic.tagmobile.protocol.SensorPacketParser.HEADER_SIZE

class DeviceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceBinding
    private val bleManager get() = TagApp.instance.bleManager
    private var pendingCsv: String? = null
    private var pendingFileName: String? = null

    private val saveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val csv = pendingCsv
        val name = pendingFileName
        if (uri == null || csv == null) {
            TagSession.recordingState = RecordingState.RECEIVED
            updateRecordingUi()
        } else {
            try {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(csv.toByteArray(Charsets.UTF_8))
                }
                val bytes = csv.toByteArray(Charsets.UTF_8).size
                Toast.makeText(
                    this,
                    "Saved ${name ?: "file.csv"} (${CsvExporter.formatFileSize(bytes)})",
                    Toast.LENGTH_LONG,
                ).show()
                TagSession.resetRecording()
                updateRecordingUi()
            } catch (e: Exception) {
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                TagSession.recordingState = RecordingState.RECEIVED
                updateRecordingUi()
            } finally {
                pendingCsv = null
                pendingFileName = null
            }
        }
    }

    private val bleListener = object : TagBleManager.Listener {
        override fun onDisconnected() {
            runOnUiThread {
                TagSession.connectedDevice = null
                TagSession.resetRecording()
                Toast.makeText(this@DeviceActivity, "Disconnected", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        override fun onPacketReceived(data: ByteArray) {
            if (TagSession.recordingState != RecordingState.RECEIVING) return
            if (TagSession.tagUptimeAtSync == null && data.size >= HEADER_SIZE) {
                val buf = java.nio.ByteBuffer.wrap(data)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                buf.position(14)
                TagSession.tagUptimeAtSync = buf.int.toLong() and 0xFFFFFFFFL
            }
            val rows = SensorPacketParser.parsePacket(
                data,
                TagSession.syncBaseUnixMs,
                TagSession.tagUptimeAtSync,
            ) ?: return
            runOnUiThread {
                TagSession.receivedRows.addAll(rows)
                TagSession.packetCount++
                updateRecordingUi()
            }
        }

        override fun onError(message: String) {
            runOnUiThread {
                Toast.makeText(this@DeviceActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val device = TagSession.connectedDevice
        if (device == null) {
            finish()
            return
        }

        binding.deviceTitle.text = device.name
        binding.backBtn.setOnClickListener { finish() }
        binding.disconnectBtn.setOnClickListener {
            bleManager.disconnect()
            TagSession.connectedDevice = null
            TagSession.resetRecording()
            finish()
        }

        binding.customDataRow.setOnClickListener {
            startActivity(Intent(this, CustomDataActivity::class.java))
        }

        binding.infoBtn.setOnClickListener { showDeviceInfo() }

        binding.customDataToggle.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                binding.customDataToggle.isChecked = false
                startActivity(Intent(this, CustomDataActivity::class.java))
            } else {
                TagSession.customDataEnabled = false
                TagSession.deviceConfig = com.nordic.tagmobile.model.DeviceConfig.default()
                updateCustomDataLabel()
            }
        }

        binding.startBtn.setOnClickListener { startRecording() }
        binding.stopBtn.setOnClickListener { stopRecording() }
        binding.saveBtn.setOnClickListener { saveRecording() }

        bleManager.listener = bleListener
        updateCustomDataLabel()
        updateRecordingUi()
    }

    override fun onResume() {
        super.onResume()
        updateCustomDataLabel()
        updateRecordingUi()
    }

    private fun updateCustomDataLabel() {
        binding.customDataMode.text =
            if (TagSession.customDataEnabled) "Custom" else "Default"
        binding.customDataToggle.isChecked = TagSession.customDataEnabled
    }

    private fun startRecording() {
        if (!bleManager.isConnected) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            return
        }
        TagSession.receivedRows.clear()
        TagSession.packetCount = 0
        TagSession.tagUptimeAtSync = null
        TagSession.recordingState = RecordingState.SYNCING
        updateRecordingUi()
        TagSession.syncBaseUnixMs = System.currentTimeMillis()
        bleManager.startRecording()
        binding.root.postDelayed({
            if (TagSession.recordingState == RecordingState.SYNCING) {
                TagSession.recordingState = RecordingState.RECEIVING
                updateRecordingUi()
            }
        }, 300)
    }

    private fun stopRecording() {
        if (TagSession.recordingState != RecordingState.RECEIVING &&
            TagSession.recordingState != RecordingState.SYNCING
        ) return
        bleManager.stopRecording()
        TagSession.recordingState = RecordingState.RECEIVED
        updateRecordingUi()
    }

    private fun saveRecording() {
        if (TagSession.recordingState != RecordingState.RECEIVED ||
            TagSession.receivedRows.isEmpty()
        ) return

        val device = TagSession.connectedDevice ?: return
        val defaultName = "${device.name}_${CsvExporter.formatDateTime(System.currentTimeMillis())
            .replace(":", "-").replace(".", "-")}"

        val input = android.widget.EditText(this).apply {
            setText(defaultName)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Enter CSV file name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                var name = input.text.toString().trim()
                if (name.isEmpty()) name = defaultName
                if (!name.lowercase().endsWith(".csv")) name += ".csv"
                beginSave(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun beginSave(fileName: String) {
        TagSession.recordingState = RecordingState.CONVERTING
        updateRecordingUi()
        binding.root.postDelayed({
            val csv = CsvExporter.build(TagSession.receivedRows, TagSession.includeSiUnits)
            val bytes = csv.toByteArray(Charsets.UTF_8).size
            TagSession.recordingState = RecordingState.SAVING
            binding.recordStatus.text = "Saving CSV… ${CsvExporter.formatFileSize(bytes)}"
            pendingCsv = csv
            pendingFileName = fileName
            binding.root.postDelayed({
                saveLauncher.launch(fileName)
            }, 400)
        }, 400)
    }

    private fun updateRecordingUi() {
        val state = TagSession.recordingState
        val isRunning = state == RecordingState.SYNCING || state == RecordingState.RECEIVING

        binding.startBtn.isEnabled = !isRunning
        binding.stopBtn.isEnabled = isRunning
        binding.saveBtn.visibility =
            if (state == RecordingState.RECEIVED) View.VISIBLE else View.GONE
        binding.saveBtn.isEnabled = state != RecordingState.SAVING

        when (state) {
            RecordingState.IDLE -> {
                binding.recordStatus.text = getString(R.string.status_ready)
                binding.recordMeta.text =
                    "Start sends mobile date/time to tag once. Tap Stop when finished receiving."
            }
            RecordingState.SYNCING -> {
                binding.recordStatus.text = "Syncing mobile time to tag…"
                binding.recordMeta.text = "Sending START command…"
            }
            RecordingState.RECEIVING -> {
                binding.recordStatus.text =
                    "Receiving… ${TagSession.packetCount} packets"
                binding.recordMeta.text =
                    "Synced at ${CsvExporter.formatDateTime(TagSession.syncBaseUnixMs)}. " +
                        "Rows: ${TagSession.receivedRows.size}"
            }
            RecordingState.RECEIVED -> {
                val est = CsvExporter.build(TagSession.receivedRows, TagSession.includeSiUnits)
                    .toByteArray(Charsets.UTF_8).size
                binding.recordStatus.text =
                    "Received · ${TagSession.packetCount} packets"
                binding.recordMeta.text =
                    "Ready to save CSV (~${CsvExporter.formatFileSize(est)}). " +
                        "Each row has date_time and timestamp_ms."
            }
            RecordingState.CONVERTING -> {
                binding.recordStatus.text = "Converting raw data to CSV…"
            }
            RecordingState.SAVING -> {
                // text set in beginSave
            }
        }
    }

    private fun showDeviceInfo() {
        val cfg = if (TagSession.customDataEnabled) TagSession.deviceConfig
        else com.nordic.tagmobile.model.DeviceConfig.default()
        val device = TagSession.connectedDevice ?: return
        val msg = buildString {
            appendLine(device.name)
            appendLine(device.address)
            appendLine("Signal: ${device.rssi} dBm")
            appendLine("Data mode: ${if (TagSession.customDataEnabled) "Custom" else "Default"}")
            appendLine("Samples / packet: ${cfg.samplesPerPacket}")
            appendLine("Sample period: ${cfg.samplePeriodMs} ms")
            appendLine("Hold: ${cfg.flushPkts} pkts (~${cfg.holdMs / 1000.0} s)")
            appendLine("BLE: Raw binary v8")
            appendLine(
                "CSV: ${if (TagSession.includeSiUnits) "Raw + SI" else "Raw only"}",
            )
        }
        AlertDialog.Builder(this)
            .setTitle("Device info")
            .setMessage(msg.trim())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
