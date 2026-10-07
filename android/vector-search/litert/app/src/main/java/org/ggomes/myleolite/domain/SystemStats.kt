package org.ggomes.myleolite.domain

/** Device + power snapshot for the statistics report. */
data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val onBattery: Boolean,
    val batteryPercent: Int?,
)

/** Source of device/power info. Faked in unit tests. */
interface SystemStatsProvider {
    fun snapshot(): DeviceSnapshot
}

/** Fallback when no platform provider is wired (unit tests). */
object UnknownSystemStats : SystemStatsProvider {
    override fun snapshot(): DeviceSnapshot =
        DeviceSnapshot("unknown", "unknown", onBattery = true, batteryPercent = null)
}
