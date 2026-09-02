package com.nordic.tagmobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nordic.tagmobile.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            openScanner()
        } else {
            Toast.makeText(this, R.string.ble_permission_rationale, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.scanFab.setOnClickListener {
            if (hasBlePermissions()) openScanner() else requestBlePermissions()
        }

        binding.connectedCard.setOnClickListener {
            if (TagSession.isConnected()) {
                startActivity(Intent(this, DeviceActivity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        renderHome()
    }

    private fun renderHome() {
        val device = TagSession.connectedDevice
        if (device == null) {
            binding.emptyState.visibility = View.VISIBLE
            binding.connectedCard.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.connectedCard.visibility = View.VISIBLE
            binding.deviceName.text = device.name
            binding.deviceMac.text = device.address
            binding.deviceRssi.text = "${device.rssi} dBm"
        }
    }

    private fun openScanner() {
        if (!TagApp.instance.bleManager.isBluetoothEnabled()) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, ScannerActivity::class.java))
    }

    private fun hasBlePermissions(): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestBlePermissions() {
        permissionLauncher.launch(requiredPermissions())
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
            )
        }
}
