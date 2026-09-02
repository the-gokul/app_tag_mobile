package com.nordic.tagmobile.model

data class DeviceConfig(
    val samplesPerPacket: Int = 5,
    val samplePeriodMs: Int = 50,
    val flushPkts: Int = 20,
    val bmi270Enabled: Boolean = true,
    val bmi270Hz: Int = 20,
    val bme688Enabled: Boolean = true,
    val bme688Hz: Int = 2,
    val tmp117Enabled: Boolean = true,
    val tmp117Hz: Int = 1,
) {
  val accumMs: Int get() = samplesPerPacket * samplePeriodMs
  val holdMs: Int get() = flushPkts * accumMs

  companion object {
    fun default() = DeviceConfig()
  }
}
