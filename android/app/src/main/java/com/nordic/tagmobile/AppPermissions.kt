package com.nordic.tagmobile

import android.Manifest
import android.os.Build

object AppPermissions {
    /** Permissions required for BLE scan and connect. */
    fun ble(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    add(Manifest.permission.BLUETOOTH)
                    add(Manifest.permission.BLUETOOTH_ADMIN)
                }
            }.toTypedArray()
        }

    /**
     * Optional legacy storage (Android 9 and below). CSV save uses the system file
     * picker on newer Android and does not need storage permission.
     */
    fun legacyStorage(): Array<String> =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            emptyArray()
        }

    fun all(): Array<String> = (ble().toList() + legacyStorage().toList()).distinct().toTypedArray()
}
