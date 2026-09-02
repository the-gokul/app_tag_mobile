package com.nordic.tagmobile

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nordic.tagmobile.ble.TagBleManager
import com.nordic.tagmobile.databinding.ActivityScannerBinding
import com.nordic.tagmobile.model.ConnectedDevice

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private val bleManager get() = TagApp.instance.bleManager
    private val adapter = DeviceAdapter { device, rssi -> connectTo(device, rssi) }
    private val seen = LinkedHashMap<String, ScanEntry>()

    private data class ScanEntry(val device: BluetoothDevice, val rssi: Int)

    private val bleListener = object : TagBleManager.Listener {
        override fun onScanResult(device: BluetoothDevice, rssi: Int) {
            runOnUiThread {
                seen[device.address] = ScanEntry(device, rssi)
                adapter.submit(seen.values.toList())
            }
        }

        override fun onConnected(device: BluetoothDevice) {
            runOnUiThread {
                binding.connectingOverlay.visibility = View.GONE
                TagSession.connectedDevice = ConnectedDevice(
                    name = device.name ?: "Tag",
                    address = device.address,
                    rssi = seen[device.address]?.rssi ?: -999,
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

        override fun onError(message: String) {
            runOnUiThread {
                binding.connectingOverlay.visibility = View.GONE
                Toast.makeText(this@ScannerActivity, message, Toast.LENGTH_SHORT).show()
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

        bleManager.listener = bleListener
        bleManager.startScan()
    }

    override fun onDestroy() {
        bleManager.stopScan()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun connectTo(device: BluetoothDevice, rssi: Int) {
        binding.connectingOverlay.visibility = View.VISIBLE
        bleManager.stopScan()
        bleManager.connect(device)
        seen[device.address] = ScanEntry(device, rssi)
    }

    private class DeviceAdapter(
        private val onClick: (BluetoothDevice, Int) -> Unit,
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
            holder.name.text = item.device.name ?: "Unknown"
            holder.mac.text = item.device.address
            holder.rssi.text = "${item.rssi} dBm"
            holder.itemView.setOnClickListener { onClick(item.device, item.rssi) }
        }

        override fun getItemCount(): Int = items.size

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.itemName)
            val mac: TextView = view.findViewById(R.id.itemMac)
            val rssi: TextView = view.findViewById(R.id.itemRssi)
        }
    }
}
