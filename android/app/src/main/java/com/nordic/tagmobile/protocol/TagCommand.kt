package com.nordic.tagmobile.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object TagUuids {
    const val STREAM_SERVICE = "7f5e0a10-4c1d-4b9a-9c22-a1b2c3d4e5f6"
    const val SENSOR_DATA = "7f5e0a11-4c1d-4b9a-9c22-a1b2c3d4e5f6"
    const val COMMAND = "7f5e0a12-4c1d-4b9a-9c22-a1b2c3d4e5f6"
}

object TagCommand {
    const val START: Byte = 0x01
    const val STOP: Byte = 0x02

    fun startPayload(unixMs: Long): ByteArray {
        return ByteBuffer.allocate(9)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(START)
            .putLong(unixMs)
            .array()
    }

    fun stopPayload(): ByteArray = byteArrayOf(STOP)
}
