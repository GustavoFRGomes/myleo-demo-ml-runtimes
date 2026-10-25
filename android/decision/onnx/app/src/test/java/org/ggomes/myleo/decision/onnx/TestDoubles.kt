package org.ggomes.myleo.decision.onnx

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import org.ggomes.myleo.decision.onnx.data.DecisionModelDownloader
import org.ggomes.myleo.decision.onnx.data.ReportExporter
import org.ggomes.myleo.decision.onnx.data.SystemStatsProvider
import org.ggomes.myleo.decision.onnx.data.JuliaModelFiles
import org.ggomes.myleo.decision.onnx.domain.Accelerator
import org.ggomes.myleo.decision.onnx.domain.BatterySample
import org.ggomes.myleo.decision.onnx.domain.Decider
import org.ggomes.myleo.decision.onnx.domain.DecisionOutcome
import org.ggomes.myleo.decision.onnx.domain.DecisionQuestion
import org.ggomes.myleo.decision.onnx.domain.DecisionRequest
import org.ggomes.myleo.decision.onnx.domain.DecisionTimings
import org.ggomes.myleo.decision.onnx.domain.DeviceProfile
import org.ggomes.myleo.decision.onnx.domain.ModelInfo
import org.ggomes.myleo.decision.onnx.domain.QuestionType

/** Scripted [Decider] — returns uniform probabilities, never touches the runtime. */
class FakeDecider(
    var loadInfo: ModelInfo = info(),
) : Decider {
    var loadError: Exception? = null
    var decideError: Exception? = null
    var closed = false

    /** When set, [decide] suspends once more than [pauseAfter] calls have been made. */
    var decideGate: CompletableDeferred<Unit>? = null
    var pauseAfter: Int = Int.MAX_VALUE
    private var calls = 0

    val requests = mutableListOf<DecisionRequest>()

    override suspend fun load(): ModelInfo {
        loadError?.let { throw it }
        return loadInfo
    }

    override suspend fun decide(request: DecisionRequest): DecisionOutcome {
        requests += request
        calls++
        if (calls > pauseAfter) decideGate?.await()
        decideError?.let { throw it }
        val question = request.question
        val probabilities = DoubleArray(question.options.size) { 1.0 / question.options.size }
        return outcome(
            question = question,
            probabilities = probabilities,
            choiceId = if (question.type == QuestionType.CHOICE) question.options.first().id else null,
            score = if (question.type == QuestionType.SCORE) 1.25 else null,
            noul = if (question.type == QuestionType.NOUL) 0.75 else null,
            tokenCount = 42,
        )
    }

    override fun close() {
        closed = true
    }

    companion object {
        fun info(
            accelerator: Accelerator = Accelerator.GPU,
            defaultWindow: Int = 512,
            availableWindows: List<Int> = listOf(512, 1024),
        ): ModelInfo =
            ModelInfo(
                accelerator = accelerator,
                defaultWindow = defaultWindow,
                availableWindows = availableWindows,
                tokenizerLoadMs = 1.0,
                tableLoadMs = 2.0,
                graphLoadMs = 3.0,
                totalLoadMs = 6.0,
            )

        fun outcome(
            question: DecisionQuestion,
            probabilities: DoubleArray,
            choiceId: String? = null,
            score: Double? = null,
            noul: Double? = null,
            argmax: Int = 0,
            tokenCount: Int = 42,
            window: Int = 512,
            accelerator: Accelerator = Accelerator.GPU,
            timings: DecisionTimings = DecisionTimings(1.0, 2.0, 0.0, 3.0, 4.0, 5.0, 6.0),
        ): DecisionOutcome {
            val noNoul = question.type != QuestionType.NOUL
            return DecisionOutcome(
                question = question,
                probabilities = probabilities,
                choiceId = choiceId,
                score = score,
                noul = noul,
                maxProbability = if (noNoul) probabilities.max() else null,
                argmax = argmax,
                tokenCount = tokenCount,
                window = window,
                accelerator = accelerator,
                timings = timings,
            )
        }
    }
}

/** [DecisionModelDownloader] fake; [gate] keeps it suspended so cancel can be exercised. */
class FakeDownloader(
    var error: Exception? = null,
    private val gate: CompletableDeferred<Unit>? = null,
    private val onComplete: () -> Unit = {},
) : DecisionModelDownloader {
    var calls = 0

    override suspend fun download(onProgress: (received: Long, total: Long) -> Unit): File {
        calls++
        onProgress(10, 100)
        gate?.await()
        error?.let { throw it }
        onComplete()
        onProgress(100, 100)
        return File("/tmp/fake-julia")
    }
}

/** A [JuliaModelFiles] pointing at paths that never need to exist for a fake decider. */
fun fakeFiles(root: File = File("/tmp/fake-julia-onnx")): JuliaModelFiles =
    JuliaModelFiles(
        directory = root,
        tokenizer = File(root, "tokenizer.json"),
        model = File(root, "model.onnx"),
    )

/** Scripted device/battery/time source; the last sample is reused once the list is exhausted. */
class FakeStats(
    var deviceProfile: DeviceProfile = DeviceProfile("TestCorp", "TestPhone", "16", 36),
    batterySamples: List<BatterySample> =
        listOf(
            BatterySample(percent = 80, chargeCounterUah = 2_000_000, currentNowUa = -350_000, temperatureC = 31.5f),
            BatterySample(percent = 79, chargeCounterUah = 1_999_000, currentNowUa = -400_000, temperatureC = 32.0f),
        ),
    timesMs: List<Long> = listOf(1_000L, 9_000L),
) : SystemStatsProvider {
    private val batteries = batterySamples.toMutableList()
    private val times = timesMs.toMutableList()

    override fun device(): DeviceProfile = deviceProfile

    override fun battery(): BatterySample =
        if (batteries.size > 1) batteries.removeAt(0) else batteries.first()

    override fun nowMs(): Long = if (times.size > 1) times.removeAt(0) else times.first()
}

/** Captures exported content so formatting can be asserted without MediaStore. */
class FakeExporter(private var result: Result<String> = Result.success("Downloads/report.md")) : ReportExporter {
    val calls = mutableListOf<Triple<String, String, String>>()

    override fun export(fileName: String, mimeType: String, content: String): Result<String> {
        calls += Triple(fileName, mimeType, content)
        return result
    }

    fun failWith(error: Exception) {
        result = Result.failure(error)
    }
}
