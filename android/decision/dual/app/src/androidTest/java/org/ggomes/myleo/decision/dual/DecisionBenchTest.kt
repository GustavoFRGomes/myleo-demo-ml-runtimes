package org.ggomes.myleo.decision.dual

import android.os.BatteryManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.ggomes.myleo.decision.dual.data.LiteRtDecider
import org.ggomes.myleo.decision.dual.data.LiteRtGraphRunner
import org.ggomes.myleo.decision.dual.data.LiteRtModelLocator
import org.ggomes.myleo.decision.dual.data.OnnxDecider
import org.ggomes.myleo.decision.dual.data.OnnxGraphRunner
import org.ggomes.myleo.decision.dual.data.OnnxModelLocator
import org.ggomes.myleo.decision.dual.domain.Decider
import org.ggomes.myleo.decision.dual.domain.DecisionRequest
import org.ggomes.myleo.decision.dual.domain.DecisionSuite
import org.ggomes.myleo.decision.dual.domain.QuestionType
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device Julia-1 suite benchmark for the dual leaf: loads one runtime, runs
 * the 18 built-in prompts [REPEATS] times, and writes a JSON summary (load ms,
 * per-prompt latency, charge-counter delta) to the app's internal files dir.
 *
 * ```
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=…DecisionBenchTest#litert
 * ```
 */
@RunWith(AndroidJUnit4::class)
class DecisionBenchTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val battery: BatteryManager by lazy { context.getSystemService(BatteryManager::class.java) }
    private val repeats: Int = InstrumentationRegistry.getArguments()
        .getString("repeats")?.toIntOrNull()?.coerceAtLeast(1) ?: REPEATS

    @Test
    fun litert() {
        val files = LiteRtModelLocator.locate(context)
        Assume.assumeTrue("julia-1-litert not staged", files != null)
        bench("litert") { LiteRtDecider(files!!, LiteRtGraphRunner.factory(context)) }
    }

    @Test
    fun onnx() {
        val files = OnnxModelLocator.locate(context)
        Assume.assumeTrue("julia-1-onnx not staged", files != null)
        bench("onnx") { OnnxDecider(files!!) { model, window -> OnnxGraphRunner.create(model, window) } }
    }

    private fun bench(runtime: String, makeDecider: () -> Decider) = runBlocking {
        val chargeStart = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val levelStart = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        Log.i(TAG, "BENCH|start|$runtime|${System.currentTimeMillis()}")
        val decider = makeDecider()
        val info = decider.load()

        val totals = ArrayList<Double>(repeats * DecisionSuite.Cases.size)
        val byType = LinkedHashMap<String, MutableList<Double>>()
        var lastTokens = 0
        var lastWindow = 0
        var lastAccelerator = ""

        repeat(repeats) {
            for (case in DecisionSuite.Cases) {
                val request = DecisionRequest(case.state, case.question)
                val t0 = System.nanoTime()
                val outcome = decider.decide(request)
                val ms = (System.nanoTime() - t0) / 1_000_000.0
                totals += ms
                byType.getOrPut(case.group.name.lowercase()) { ArrayList() } += ms
                lastTokens = outcome.tokenCount
                lastWindow = outcome.window
                lastAccelerator = outcome.accelerator.label
                assertTrue("probabilities must be finite", outcome.probabilities.all { it.isFinite() })
            }
        }
        decider.close()

        val chargeEnd = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val levelEnd = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val sorted = totals.sorted()
        val chargeUah = chargeEnd - chargeStart
        val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

        val perType = byType.entries.joinToString(",\n    ") { (name, values) ->
            val sortedType = values.sorted()
            "\"$name\": {\"count\": ${values.size}, \"avgMs\": ${fmt(values.average())}, " +
                "\"minMs\": ${fmt(sortedType.first())}, \"maxMs\": ${fmt(sortedType.last())}}"
        }

        val json = """
            {
              "area": "decision",
              "runtime": "$runtime",
              "device": "$device",
              "android": "${android.os.Build.VERSION.RELEASE}",
              "backend": "${info.backend}",
              "accelerator": "$lastAccelerator",
              "window": $lastWindow,
              "tokens": $lastTokens,
              "loadMs": ${fmt(info.totalLoadMs)},
              "tokenizerLoadMs": ${fmt(info.tokenizerLoadMs)},
              "tableLoadMs": ${fmt(info.tableLoadMs)},
              "graphLoadMs": ${fmt(info.graphLoadMs)},
              "prompts": ${totals.size},
              "avgMs": ${fmt(totals.average())},
              "minMs": ${fmt(sorted.first())},
              "maxMs": ${fmt(sorted.last())},
              "wallSec": ${fmt(totals.sum() / 1000.0)},
              "chargeCounterUah": $chargeUah,
              "batteryStartPct": $levelStart,
              "batteryEndPct": $levelEnd,
              "perType": {
                $perType
              }
            }
        """.trimIndent()
        val out = File(context.filesDir, "bench-decision-$runtime.json")
        out.writeText(json)
        Log.i(TAG, "BENCH|decision|runtime=$runtime|backend=${info.backend}|loadMs=${fmt(info.totalLoadMs)}|" +
            "avgMs=${fmt(totals.average())}|prompts=${totals.size}|chargeUah=$chargeUah|" +
            "level=$levelStart->$levelEnd|window=$lastWindow|tokens=$lastTokens")
        Log.i(TAG, "BENCH|json|$runtime|${out.absolutePath}")
        Log.i(TAG, "BENCH|done|$runtime|${System.currentTimeMillis()}")
        Unit
    }

    private fun fmt(value: Double) = String.format(Locale.US, "%.2f", value)

    private companion object {
        const val TAG = "MyLeoBench"
        const val REPEATS = 3
    }
}
