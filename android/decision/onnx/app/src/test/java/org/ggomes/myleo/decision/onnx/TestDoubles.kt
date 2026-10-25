package org.ggomes.myleo.decision.onnx

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import org.ggomes.myleo.decision.onnx.data.DecisionModelDownloader
import org.ggomes.myleo.decision.onnx.data.JuliaModelFiles
import org.ggomes.myleo.decision.onnx.domain.Accelerator
import org.ggomes.myleo.decision.onnx.domain.Decider
import org.ggomes.myleo.decision.onnx.domain.DecisionOutcome
import org.ggomes.myleo.decision.onnx.domain.DecisionQuestion
import org.ggomes.myleo.decision.onnx.domain.DecisionRequest
import org.ggomes.myleo.decision.onnx.domain.DecisionTimings
import org.ggomes.myleo.decision.onnx.domain.ModelInfo
import org.ggomes.myleo.decision.onnx.domain.QuestionType

/** Scripted [Decider] — returns uniform probabilities, never touches the runtime. */
class FakeDecider(
    var loadInfo: ModelInfo = info(),
) : Decider {
    var loadError: Exception? = null
    var decideError: Exception? = null
    var closed = false
    val requests = mutableListOf<DecisionRequest>()

    override suspend fun load(): ModelInfo {
        loadError?.let { throw it }
        return loadInfo
    }

    override suspend fun decide(request: DecisionRequest): DecisionOutcome {
        requests += request
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
        fun info(): ModelInfo =
            ModelInfo(
                accelerator = Accelerator.GPU,
                defaultWindow = 512,
                availableWindows = listOf(512, 1024),
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
                timings = DecisionTimings(1.0, 2.0, 0.0, 3.0, 4.0, 5.0, 6.0),
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
