package com.nordic.tagmobile.protocol

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CsvExporter {
    fun header(includeSi: Boolean): String {
        val raw = listOf(
            "timestamp_ms", "sample_number", "flags",
            "accel_x", "accel_y", "accel_z",
            "gyro_x", "gyro_y", "gyro_z",
            "humidity_x100", "env_temp_x100", "body_temp_x100",
        )
        val si = if (!includeSi) emptyList() else listOf(
            "accel_x_mps2", "accel_y_mps2", "accel_z_mps2",
            "gyro_x_rads", "gyro_y_rads", "gyro_z_rads",
            "humidity_pct", "env_temp_c", "body_temp_c",
        )
        return (raw + si + "date_time").joinToString(",")
    }

    fun row(row: SensorCsvRow, includeSi: Boolean): String {
        val raw = listOf(
            row.timestampMs,
            row.sampleNumber,
            "0x%02X".format(row.flags),
            row.accelX, row.accelY, row.accelZ,
            row.gyroX, row.gyroY, row.gyroZ,
            row.humidityX100, row.envTempX100, row.bodyTempX100,
        )
        val si = if (!includeSi) emptyList() else listOf(
            row.accelX / 1000.0,
            row.accelY / 1000.0,
            row.accelZ / 1000.0,
            row.gyroX / 1000.0,
            row.gyroY / 1000.0,
            row.gyroZ / 1000.0,
            row.humidityX100 / 100.0,
            row.envTempX100 / 100.0,
            row.bodyTempX100 / 100.0,
        )
        return (raw + si + row.dateTime).joinToString(",")
    }

    fun build(rows: List<SensorCsvRow>, includeSi: Boolean): String {
        val lines = ArrayList<String>(rows.size + 1)
        lines.add(header(includeSi))
        rows.forEach { lines.add(row(it, includeSi)) }
        return lines.joinToString("\n")
    }

    fun formatDateTime(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        fmt.timeZone = TimeZone.getDefault()
        return fmt.format(Date(epochMs))
    }

    fun formatFileSize(bytes: Int): String =
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
}
