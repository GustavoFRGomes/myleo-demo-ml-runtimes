package org.ggomes.myleo.vectorsearch.dual.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import org.ggomes.myleo.vectorsearch.dual.domain.DeviceSnapshot
import org.ggomes.myleo.vectorsearch.dual.domain.SystemStatsProvider

/** [SystemStatsProvider] reading `Build` fields + the sticky battery intent (no permissions needed). */
class AndroidSystemStatsProvider(context: Context) : SystemStatsProvider {

    private val appContext = context.applicationContext

    override fun snapshot(): DeviceSnapshot {
        val battery = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else null
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            onBattery = !charging,
            batteryPercent = percent,
        )
    }
}
