package org.ggomes.myleo.decision.litert.domain

import java.util.Locale

/** Device identity for a benchmark report; no permissions are needed to read these. */
data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val sdk: Int,
)

/**
 * One battery reading. [chargeCounterUah] and [currentNowUa] are null when the device does not
 * expose `BatteryManager`'s corresponding property; the report then says so instead of inventing
 * a number.
 */
data class BatterySample(
    val percent: Int?,
    val chargeCounterUah: Long?,
    val currentNowUa: Long?,
    val temperatureC: Float?,
)

/** Performance for one question type across the suite. */
data class TypeAggregate(
    val type: QuestionType,
    val count: Int,
    val promptMs: Double,
    val minMs: Double,
    val maxMs: Double,
) {
    val averageMs: Double get() = if (count == 0) 0.0 else promptMs / count
}

/**
 * Benchmark of one suite run: model/runtime identity, load timings, per-prompt performance and
 * the battery delta sampled around the run. Pure data so formatting and export are unit-testable.
 */
data class SuiteReport(
    val runtimeLabel: String,
    val backend: String,
    val window: Int,
    val device: DeviceProfile,
    val loadMs: Double,
    val tokenizerLoadMs: Double,
    val graphLoadMs: Double,
    val batteryStart: BatterySample,
    val batteryEnd: BatterySample,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
    val results: List<SuiteResult>,
) {
    init {
        require(results.isNotEmpty()) { "a report needs at least one prompt result" }
    }

    /** Sum of the per-prompt totals (excludes UI overhead between prompts). */
    val promptMs: Double = results.sumOf { it.outcome.timings.totalMs }

    /** Wall clock around the whole run, including encoding and UI updates. */
    val wallMs: Long = (finishedAtEpochMs - startedAtEpochMs).coerceAtLeast(0L)

    val averageMs: Double = promptMs / results.size

    val minMs: Double = results.minOf { it.outcome.timings.totalMs }

    val maxMs: Double = results.maxOf { it.outcome.timings.totalMs }

    /** Battery charge used during the run; null when the charge counter is unavailable. */
    val chargeDeltaUah: Long? =
        if (batteryStart.chargeCounterUah != null && batteryEnd.chargeCounterUah != null) {
            batteryEnd.chargeCounterUah - batteryStart.chargeCounterUah
        } else {
            null
        }

    val chargeDeltaMah: Double? = chargeDeltaUah?.let { it / 1000.0 }

    fun aggregate(type: QuestionType): TypeAggregate {
        val rows = results.filter { it.case.group == type }
        return TypeAggregate(
            type = type,
            count = rows.size,
            promptMs = rows.sumOf { it.outcome.timings.totalMs },
            minMs = rows.minOfOrNull { it.outcome.timings.totalMs } ?: 0.0,
            maxMs = rows.maxOfOrNull { it.outcome.timings.totalMs } ?: 0.0,
        )
    }

    val aggregates: List<TypeAggregate> get() = QuestionType.entries.map(::aggregate)

    val fileNameStem: String
        get() = "julia-1-suite-${runtimeLabel.lowercase(Locale.US).replace(' ', '-')}"
}
