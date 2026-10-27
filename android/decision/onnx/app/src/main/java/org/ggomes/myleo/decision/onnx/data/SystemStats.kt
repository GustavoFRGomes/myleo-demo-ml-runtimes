package org.ggomes.myleo.decision.onnx.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import org.ggomes.myleo.decision.onnx.domain.BatterySample
import org.ggomes.myleo.decision.onnx.domain.DeviceProfile

/**
 * Device + battery readings for a benchmark report. A seam so the ViewModel's report building is
 * unit-tested with fixed samples instead of a real phone.
 */
interface SystemStatsProvider {
    fun device(): DeviceProfile

    fun battery(): BatterySample

    fun nowMs(): Long
}

/**
 * [SystemStatsProvider] over `Build` and the sticky battery intent. No permissions are needed.
 *
 * The charge counter is the honest way to attribute energy to a run; many devices do not expose
 * it (or report 0), so it becomes null and the report says "unavailable" rather than a fake 0.
 */
class AndroidSystemStatsProvider(context: Context) : SystemStatsProvider {

    private val appContext = context.applicationContext
    private val batteryManager = appContext.getSystemService(BatteryManager::class.java)

    override fun device(): DeviceProfile =
        DeviceProfile(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
        )

    override fun nowMs(): Long = System.currentTimeMillis()

    override fun battery(): BatterySample {
        val sticky = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else null
        val temperature =
            sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeUnless { it == Int.MIN_VALUE }
                ?.let { it / 10f }

        // CHARGE_COUNTER is microamp-hours (µAh); CURRENT_NOW is microamps (µA).
        val chargeCounter =
            runCatching { batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) }
                .getOrNull()
                ?.takeIf { it > 0L }
        val currentNow =
            runCatching { batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }
                .getOrNull()
                ?.takeUnless { it == Long.MIN_VALUE }

        return BatterySample(
            percent = percent,
            chargeCounterUah = chargeCounter,
            currentNowUa = currentNow,
            temperatureC = temperature,
        )
    }
}
