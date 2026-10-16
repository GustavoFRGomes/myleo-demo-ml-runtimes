package org.ggomes.myleo.llm.dual.data

import android.app.ActivityManager
import android.content.Context

/**
 * The device facts and remembered outcome the engine consults before attempting a GPU
 * load. A seam so the decision is injectable: [Allow] for tests / non-Android callers,
 * [AndroidGpuPolicy] in the app.
 */
interface GpuPolicy {
    /** Total physical RAM in bytes. */
    fun totalRamBytes(): Long

    /** Outcome of the last GPU attempt — see [GpuVerdict]. */
    fun verdict(): GpuVerdict

    /** Remember [verdict] across process lifetimes. Must be durable before returning. */
    fun record(verdict: GpuVerdict)

    companion object {
        /** Always allow GPU and remember nothing — the no-gate default. */
        val Allow: GpuPolicy = object : GpuPolicy {
            override fun totalRamBytes(): Long = Long.MAX_VALUE
            override fun verdict(): GpuVerdict = GpuVerdict.UNKNOWN
            override fun record(verdict: GpuVerdict) = Unit
        }
    }
}

/**
 * [GpuPolicy] backed by `ActivityManager` (total RAM) and `SharedPreferences` (verdict).
 *
 * [record] commits synchronously on purpose: [GpuVerdict.PENDING] must be on disk
 * *before* the native `initialize()` call it guards, otherwise a kill would erase the
 * only evidence of it. Only ever called from the engine's background load, never the
 * main thread.
 */
class AndroidGpuPolicy(context: Context) : GpuPolicy {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun totalRamBytes(): Long {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem
    }

    override fun verdict(): GpuVerdict {
        val stored = prefs.getString(KEY_VERDICT, null) ?: return GpuVerdict.UNKNOWN
        return runCatching { GpuVerdict.valueOf(stored) }.getOrDefault(GpuVerdict.UNKNOWN)
    }

    override fun record(verdict: GpuVerdict) {
        prefs.edit().putString(KEY_VERDICT, verdict.name).commit()
    }

    private companion object {
        const val PREFS = "gpu_policy"
        const val KEY_VERDICT = "gpu_verdict"
    }
}
