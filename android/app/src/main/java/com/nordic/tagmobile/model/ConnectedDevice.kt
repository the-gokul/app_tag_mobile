package com.nordic.tagmobile.model

data class ConnectedDevice(
    val name: String,
    val address: String,
    var rssi: Int,
)
