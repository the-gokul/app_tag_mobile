package com.nordic.tagmobile

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nordic.tagmobile.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingOpenScanner = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val bleOk = AppPermissions.ble().all { grants[it] == true }
        if (bleOk) {
            ensureBluetoothEnabled()
        } else {
            Toast.makeText(this, R.string.ble_permission_rationale, Toast.LENGTH_LONG).show()
            pendingOpenScanner = false
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (pendingOpenScanner && TagApp.instance.bleManager.isBluetoothEnabled()) {
            pendingOpenScanner = false
            startActivity(Intent(this, ScannerActivity::class.java))
        } else if (pendingOpenScanner) {
            pendingOpenScanner = false
            Toast.makeText(this, R.string.bluetooth_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.scanFab.setOnClickListener { beginScanFlow() }

        binding.connectedCard.setOnClickListener {
            if (TagSession.isConnected()) {
                startActivity(Intent(this, DeviceActivity::class.java))
            }
        }

        requestAppPermissionsOnLaunch()
    }

    override fun onResume() {
        super.onResume()
        renderHome()
    }

    private fun requestAppPermissionsOnLaunch() {
        val missing = AppPermissions.all().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.permissions_title)
                .setMessage(R.string.permissions_message)
                .setPositiveButton(R.string.grant_permissions) { _, _ ->
                    permissionLauncher.launch(missing.toTypedArray())
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun beginScanFlow() {
        val missingBle = AppPermissions.ble().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingBle.isNotEmpty()) {
            permissionLauncher.launch(missingBle.toTypedArray())
            pendingOpenScanner = true
            return
        }
        ensureBluetoothEnabled(andOpenScanner = true)
    }

    private fun ensureBluetoothEnabled(andOpenScanner: Boolean = false) {
        if (andOpenScanner) pendingOpenScanner = true
        if (TagApp.instance.bleManager.isBluetoothEnabled()) {
            if (pendingOpenScanner) {
                pendingOpenScanner = false
                startActivity(Intent(this, ScannerActivity::class.java))
            }
            return
        }
        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
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
}
