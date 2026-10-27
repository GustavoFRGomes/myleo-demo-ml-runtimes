package org.ggomes.myleo.vectorsearch.dual

import android.os.BatteryManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.ggomes.myleo.vectorsearch.dual.data.LiteRtEmbedderFactory
import org.ggomes.myleo.vectorsearch.dual.data.OnnxEmbedderFactory
import org.ggomes.myleo.vectorsearch.dual.domain.Embedder
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device embedding benchmark for the dual leaf: loads one runtime, embeds the
 * fixed corpus [REPEATS] times, and writes a JSON row (load ms, median/mean
 * ms per chunk, charge-counter delta) to the app's external files dir.
 *
 * Run each lane in its own process so both 90 MB models are never resident
 * together on low-RAM phones:
 *
 * ```
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=…EmbeddingBenchTest#litert
 * ```
 */
@RunWith(AndroidJUnit4::class)
class EmbeddingBenchTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val battery: BatteryManager by lazy { context.getSystemService(BatteryManager::class.java) }
    private val repeats: Int = InstrumentationRegistry.getArguments()
        .getString("repeats")?.toIntOrNull()?.coerceAtLeast(1) ?: REPEATS

    @Test
    fun litert() = bench("litert") { LiteRtEmbedderFactory.create(context) }

    @Test
    fun onnx() = bench("onnx") { OnnxEmbedderFactory.create(context) }

    private fun bench(runtime: String, factory: () -> Embedder) {
        val chargeStart = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val levelStart = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        Log.i(TAG, "BENCH|start|$runtime|${System.currentTimeMillis()}")
        val loadStart = System.nanoTime()
        val embedder = factory()
        val loadMs = (System.nanoTime() - loadStart) / 1_000_000.0

        val times = ArrayList<Double>(repeats * CORPUS.size)
        repeat(repeats) {
            for (text in CORPUS) {
                val t0 = System.nanoTime()
                val vector = embedder.embed(text)
                times += (System.nanoTime() - t0) / 1_000_000.0
                assertTrue("expected a 384-d vector, got ${vector.size}", vector.size == 384)
            }
        }
        (embedder as? AutoCloseable)?.close()

        val chargeEnd = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val levelEnd = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val sorted = times.sorted()
        val median = sorted[sorted.size / 2]
        val mean = times.average()
        val chargeUah = chargeEnd - chargeStart
        val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

        val json = """
            {
              "area": "vector-search",
              "runtime": "$runtime",
              "device": "$device",
              "android": "${android.os.Build.VERSION.RELEASE}",
              "loadMs": ${fmt(loadMs)},
              "chunks": ${times.size},
              "medianMs": ${fmt(median)},
              "meanMs": ${fmt(mean)},
              "minMs": ${fmt(sorted.first())},
              "maxMs": ${fmt(sorted.last())},
              "chargeCounterUah": $chargeUah,
              "batteryStartPct": $levelStart,
              "batteryEndPct": $levelEnd,
              "wallSec": ${fmt(times.sum() / 1000.0)}
            }
        """.trimIndent()
        val out = File(context.filesDir, "bench-embedding-$runtime.json")
        out.writeText(json)
        Log.i(TAG, "BENCH|embedding|runtime=$runtime|loadMs=${fmt(loadMs)}|" +
            "medianMs=${fmt(median)}|meanMs=${fmt(mean)}|chunks=${times.size}|" +
            "chargeUah=$chargeUah|level=$levelStart->$levelEnd")
        Log.i(TAG, "BENCH|json|$runtime|${out.absolutePath}")
        Log.i(TAG, "BENCH|done|$runtime|${System.currentTimeMillis()}")
    }

    private fun fmt(value: Double) = String.format(Locale.US, "%.2f", value)

    private companion object {
        const val TAG = "MyLeoBench"
        const val REPEATS = 15

        val CORPUS = listOf(
            "The refrigerator keeps food cold by moving heat from inside the cabinet to the room.",
            "A compressor circulates refrigerant through the coils and the expansion valve drops its pressure.",
            "The thermostat cycles the compressor on and off to hold the set temperature.",
            "Freezers reach about minus eighteen degrees Celsius to preserve food for months.",
            "Quarterly revenue exceeded expectations after the new product launch.",
            "The sales team added three enterprise accounts in the northern region.",
            "A heat pump moves thermal energy in the opposite direction to a fridge.",
            "Insulation slows heat transfer so the cabinet stays cold with less work.",
            "The defrost cycle briefly warms the evaporator to melt accumulated frost.",
            "Server logs showed a spike in latency during the nightly backup window.",
            "The compressor draws more current at startup than while running steadily.",
            "A dirty condenser coil makes the fridge run longer and use more energy.",
            "The invoice lists a prorated charge for the partial month of service.",
            "Support replied within the hour and resolved the login problem.",
            "The parcel arrived two days late but the contents were undamaged.",
            "Our team migrated the analytics job to the new cluster last weekend.",
            "The battery drains quickly when the display brightness is at maximum.",
            "The contract renewal is due at the end of the next quarter.",
            "A refund was issued after the duplicate charge was confirmed.",
            "The model card documents the training data and known limitations.",
        )
    }
}
