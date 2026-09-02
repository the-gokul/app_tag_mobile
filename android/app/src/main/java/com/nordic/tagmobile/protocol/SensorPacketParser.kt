package com.nordic.tagmobile.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SensorCsvRow(
    val timestampMs: Long,
    val dateTime: String,
    val sampleNumber: Long,
    val flags: Int,
    val accelX: Int,
    val accelY: Int,
    val accelZ: Int,
    val gyroX: Int,
    val gyroY: Int,
    val gyroZ: Int,
    val humidityX100: Int,
    val envTempX100: Int,
    val bodyTempX100: Int,
)

object SensorPacketParser {
    const val START_BYTE = 0xA1
    const val STOP_BYTE = 0x5A
    const val VERSION = 8
    const val HEADER_SIZE = 18
    const val READING_SIZE = 19
    const val SAMPLE_WIRE_SIZE = 21
    const val MAX_SAMPLES = 10

    fun parsePacket(
        data: ByteArray,
        syncBaseUnixMs: Long,
        tagUptimeAtSync: Long?,
    ): List<SensorCsvRow>? {
        if (data.size < HEADER_SIZE + SAMPLE_WIRE_SIZE + 1) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val start = buf.get().toInt() and 0xFF
        if (start != START_BYTE) return null
        val version = buf.get().toInt() and 0xFF
        if (version != VERSION) return null
        buf.int // serial
        buf.int // packet id
        val firstSampleNumber = buf.int.toLong() and 0xFFFFFFFFL
        val baseTimestampMs = buf.int.toLong() and 0xFFFFFFFFL

        val trailerIndex = data.size - 1
        if ((data[trailerIndex].toInt() and 0xFF) != STOP_BYTE) return null

        val sampleCount = (data.size - HEADER_SIZE - 1) / SAMPLE_WIRE_SIZE
        if (sampleCount < 1 || sampleCount > MAX_SAMPLES) return null

        val rows = ArrayList<SensorCsvRow>(sampleCount)
        for (i in 0 until sampleCount) {
            val flags = buf.get().toInt() and 0xFF
            val accelX = buf.short.toInt()
            val accelY = buf.short.toInt()
            val accelZ = buf.short.toInt()
            val gyroX = buf.short.toInt()
            val gyroY = buf.short.toInt()
            val gyroZ = buf.short.toInt()
            val humidity = buf.short.toInt() and 0xFFFF
            val envTemp = buf.short.toInt()
            val bodyTemp = buf.short.toInt()
            val deltaMs = buf.short.toInt() and 0xFFFF

            val rawTimestampMs = baseTimestampMs + deltaMs
            val relativeMs = if (tagUptimeAtSync != null) {
                rawTimestampMs - tagUptimeAtSync
            } else {
                rawTimestampMs
            }
            val absMs = syncBaseUnixMs + relativeMs
            rows.add(
                SensorCsvRow(
                    timestampMs = relativeMs,
                    dateTime = CsvExporter.formatDateTime(absMs),
                    sampleNumber = firstSampleNumber + i,
                    flags = flags,
                    accelX = accelX,
                    accelY = accelY,
                    accelZ = accelZ,
                    gyroX = gyroX,
                    gyroY = gyroY,
                    gyroZ = gyroZ,
                    humidityX100 = humidity,
                    envTempX100 = envTemp,
                    bodyTempX100 = bodyTemp,
                ),
            )
        }
        return rows
    }
}
