package com.nordic.tagmobile

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nordic.tagmobile.ble.TagBleManager
import com.nordic.tagmobile.ble.TagBleScanner
import com.nordic.tagmobile.databinding.ActivityScannerBinding
import com.nordic.tagmobile.debug.AgentDebugLog
import com.nordic.tagmobile.log.LogCategory
import com.nordic.tagmobile.log.TagLogger
import com.nordic.tagmobile.model.ConnectedDevice

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private val bleManager get() = TagApp.instance.bleManager
    private val bleScanner get() = TagApp.instance.bleScanner
    private val adapter = DeviceAdapter { device, rssi, name -> connectTo(device, rssi, name) }
    private val seen = LinkedHashMap<String, ScanEntry>()
    private var pendingName = "Tag"
    private var pendingRssi = -999

    private data class ScanEntry(
        val device: BluetoothDevice,
        val rssi: Int,
        val displayName: String,
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (AppPermissions.ble().all { grants[it] == true }) {
            startScanning()
        } else {
            Toast.makeText(this, R.string.ble_permission_rationale, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val scanListener = object : TagBleScanner.Listener {
        override fun onDevice(device: BluetoothDevice, rssi: Int, name: String) {
            runOnUiThread {
                val prev = seen[device.address]
                val overwritten = prev != null &&
                    prev.displayName != "Unknown" &&
                    (name.isBlank() || name == "Unknown")
                val topBefore = seen.values.maxByOrNull { it.rssi }?.device?.address
                // Current behavior (measure): always replace entry, then re-sort by RSSI
                seen[device.address] = ScanEntry(device, rssi, name)
                val sorted = seen.values.toList().sortedByDescending { it.rssi }
                val topAfter = sorted.firstOrNull()?.device?.address
                // #region agent log
                if (seen.size <= 30) {
                    AgentDebugLog.init(this@ScannerActivity)
                    AgentDebugLog.log(
                        hypothesisId = "A_B",
                        location = "ScannerActivity.onDevice",
                        message = "list_update",
                        data = mapOf(
                            "address" to device.address,
                            "incomingName" to name,
                            "prevName" to (prev?.displayName ?: ""),
                            "overwroteGoodName" to overwritten,
                            "prevRssi" to (prev?.rssi ?: 999),
                            "newRssi" to rssi,
                            "listSize" to seen.size,
                            "topBefore" to (topBefore ?: ""),
                            "topAfter" to (topAfter ?: ""),
                            "orderChanged" to (topBefore != null && topBefore != topAfter),
                            "sortedByRssi" to true,
                        ),
                    )
                }
                // #endregion
                adapter.submit(sorted)
                binding.emptyScanState.visibility = View.GONE
                binding.scanCount.text = getString(R.string.devices_found, seen.size)
            }
        }

        override fun onError(message: String) {
            runOnUiThread {
                TagLogger.log(LogCategory.ERRORS, "SCAN_ERROR", message)
                Toast.makeText(this@ScannerActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val bleListener = object : TagBleManager.Listener {
        override fun onReady(device: BluetoothDevice) {
            runOnUiThread {
                binding.connectingOverlay.visibility = View.GONE
                TagLogger.log(
                    LogCategory.BLE,
                    "CONNECT_OK",
                    "$pendingName ${device.address} rssi=$pendingRssi",
                )
                TagSession.connectedDevice = ConnectedDevice(
                    name = pendingName.ifBlank { device.name ?: "Tag" },
                    address = device.address,
                    rssi = pendingRssi,
                )
                TagSession.resetRecording()
                startActivity(Intent(this@ScannerActivity, DeviceActivity::class.java))
                finish()
            }
        }

        override fun onDisconnected() {
            runOnUiThread {
                binding.connectingOverlay.visibility = View.GONE
            }
        }

        override fun onPacket(data: ByteArray) = Unit

        override fun onError(message: String) {
            runOnUiThread {
                binding.connectingOverlay.visibility = View.GONE
                TagLogger.log(LogCategory.ERRORS, "CONNECT_FAIL", message)
                Toast.makeText(this@ScannerActivity, message, Toast.LENGTH_LONG).show()
                bleScanner.start()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backBtn.setOnClickListener { finish() }
        binding.deviceList.layoutManager = LinearLayoutManager(this)
        binding.deviceList.adapter = adapter
        binding.scanCount.text = getString(R.string.devices_found, 0)

        if (!hasBlePermissions()) {
            permissionLauncher.launch(AppPermissions.ble())
            return
        }
        startScanning()
    }

    private fun startScanning() {
        bleManager.listener = bleListener
        bleScanner.listener = scanListener
        TagLogger.log(LogCategory.BLE, "SCAN_START", "all BLE devices")
        // #region agent log
        AgentDebugLog.init(this)
        Toast.makeText(this, "Debug log: ${AgentDebugLog.path()}", Toast.LENGTH_LONG).show()
        // #endregion
        bleScanner.start()
    }

    private fun hasBlePermissions(): Boolean =
        AppPermissions.ble().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    override fun onDestroy() {
        TagLogger.log(LogCategory.BLE, "SCAN_STOP", "devices_seen=${seen.size}")
        bleScanner.stop()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun connectTo(device: BluetoothDevice, rssi: Int, name: String) {
        pendingName = name
        pendingRssi = rssi
        binding.connectingOverlay.visibility = View.VISIBLE
        bleScanner.stop()
        TagLogger.log(LogCategory.BLE, "CONNECT_ATTEMPT", "$name ${device.address}")
        bleManager.connectTag(device)
    }

    private class DeviceAdapter(
        private val onClick: (BluetoothDevice, Int, String) -> Unit,
    ) : RecyclerView.Adapter<DeviceAdapter.Holder>() {

        private var items: List<ScanEntry> = emptyList()

        fun submit(list: List<ScanEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scan_device, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.name.text = item.displayName
            holder.mac.text = item.device.address
            holder.rssi.text = "${item.rssi} dBm"
            holder.itemView.setOnClickListener {
                onClick(item.device, item.rssi, item.displayName)
            }
        }

        override fun getItemCount(): Int = items.size

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.itemName)
            val mac: TextView = view.findViewById(R.id.itemMac)
            val rssi: TextView = view.findViewById(R.id.itemRssi)
        }
    }
}
