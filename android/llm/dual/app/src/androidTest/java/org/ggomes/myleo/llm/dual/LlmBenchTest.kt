package org.ggomes.myleo.llm.dual

import android.os.BatteryManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.ggomes.myleo.llm.dual.data.AndroidGpuPolicy
import org.ggomes.myleo.llm.dual.data.LiteRTLMEngine
import org.ggomes.myleo.llm.dual.data.LiteRTLMModelLocator
import org.ggomes.myleo.llm.dual.data.OnnxGenAIEngine
import org.ggomes.myleo.llm.dual.data.OnnxGenAIModelLocator
import org.ggomes.myleo.llm.dual.domain.ChatInference
import org.ggomes.myleo.llm.dual.domain.ChatMessage
import org.ggomes.myleo.llm.dual.domain.ChatRole
import org.ggomes.myleo.llm.dual.domain.GenerationParams
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device generation benchmark for the dual leaf: loads one runtime, runs the
 * fixed prompt [repeats] times (default 3) with the production defaults, and
 * writes a JSON summary (load, TTFT, tok/s, tokens, charge-counter delta) to
 * the app's internal files dir.
 *
 * ```
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=…LlmBenchTest#litert \
 *   -Pandroid.testInstrumentationRunnerArguments.repeats=3
 * ```
 */
@RunWith(AndroidJUnit4::class)
class LlmBenchTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val battery: BatteryManager by lazy { context.getSystemService(BatteryManager::class.java) }
    private val repeats: Int = InstrumentationRegistry.getArguments()
        .getString("repeats")?.toIntOrNull()?.coerceAtLeast(1) ?: 3

    @Test
    fun litert() {
        val file = LiteRTLMModelLocator.locate(context)
        Assume.assumeTrue("litertlm not staged", file != null)
        bench("litert") { LiteRTLMEngine(file!!.absolutePath, AndroidGpuPolicy(context)) }
    }

    @Test
    fun onnx() {
        val dir = OnnxGenAIModelLocator.locate(context)
        Assume.assumeTrue("Phi-4 int4 package not staged", dir != null)
        bench("onnx") { OnnxGenAIEngine(dir!!.absolutePath, OnnxGenAIModelLocator.packageSize(dir)) }
    }

    private fun bench(runtime: String, makeEngine: () -> ChatInference) = runBlocking {
        val chargeStart = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val levelStart = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        Log.i(TAG, "BENCH|start|$runtime|${System.currentTimeMillis()}")
        val engine = makeEngine()
        val info = engine.loadModel()

        val runs = ArrayList<Map<String, Any>>(repeats)
        repeat(repeats) { index ->
            val tokensBefore = runCatching { engine.tokenCount() }.getOrDefault(0)
            val startedAt = System.currentTimeMillis()
            var firstChunkAt: Long? = null
            var chars = 0
            var tokens = 0
            engine.generate("", listOf(ChatMessage(role = ChatRole.USER, text = PROMPT)), PARAMS)
                .collect { chunk ->
                    if (chunk.isEmpty()) return@collect
                    if (firstChunkAt == null) firstChunkAt = System.currentTimeMillis()
                    // Both engines emit one streaming piece per token.
                    tokens += 1
                    chars += chunk.length
                }
            val endedAt = System.currentTimeMillis()
            val totalMs = endedAt - startedAt
            val ttftMs = firstChunkAt?.let { it - startedAt } ?: totalMs
            val decodeMs = (totalMs - ttftMs).coerceAtLeast(1)
            val tokPerSec = tokens / (decodeMs / 1000.0)
            runs += mapOf(
                "run" to index + 1,
                "ttftMs" to ttftMs,
                "totalMs" to totalMs,
                "tokens" to tokens,
                "tokPerSec" to tokPerSec,
                "chars" to chars,
            )
            Log.i(TAG, "BENCH|gen|runtime=$runtime|run=${index + 1}|ttftMs=$ttftMs|" +
                "tokens=$tokens|tokPerSec=${fmt(tokPerSec)}|chars=$chars")
            assertTrue("generation $index produced no tokens", tokens > 0)
        }
        engine.close()

        val chargeEnd = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val levelEnd = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val tokensTotal = runs.sumOf { (it["tokens"] as Int) }
        val wallMs = runs.sumOf { it["totalMs"] as Long }
        val chargeUah = chargeEnd - chargeStart
        val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

        val runsJson = runs.joinToString(",\n    ") { run ->
            "{\"run\": ${run["run"]}, \"ttftMs\": ${run["ttftMs"]}, \"totalMs\": ${run["totalMs"]}, " +
                "\"tokens\": ${run["tokens"]}, \"tokPerSec\": ${fmt(run["tokPerSec"] as Double)}}"
        }
        val json = """
            {
              "area": "llm",
              "runtime": "$runtime",
              "device": "$device",
              "android": "${android.os.Build.VERSION.RELEASE}",
              "backend": "${info.backend}",
              "model": "${info.displayName}",
              "loadMs": ${info.loadMs},
              "repeats": $repeats,
              "tokens": $tokensTotal,
              "wallSec": ${fmt(wallMs / 1000.0)},
              "chargeCounterUah": $chargeUah,
              "batteryStartPct": $levelStart,
              "batteryEndPct": $levelEnd,
              "runs": [
                $runsJson
              ]
            }
        """.trimIndent()
        val out = File(context.filesDir, "bench-llm-$runtime.json")
        out.writeText(json)
        Log.i(TAG, "BENCH|llm|runtime=$runtime|backend=${info.backend}|loadMs=${info.loadMs}|" +
            "tokens=$tokensTotal|wallSec=${fmt(wallMs / 1000.0)}|chargeUah=$chargeUah|" +
            "level=$levelStart->$levelEnd")
        Unit
    }

    private fun fmt(value: Double) = String.format(Locale.US, "%.1f", value)

    private companion object {
        const val TAG = "MyLeoBench"
        const val PROMPT = "Explain how a refrigerator works in detail."
        val PARAMS = GenerationParams(temperature = 0.8, topK = 40, topP = 0.95, maxTokens = 512)
    }
}
