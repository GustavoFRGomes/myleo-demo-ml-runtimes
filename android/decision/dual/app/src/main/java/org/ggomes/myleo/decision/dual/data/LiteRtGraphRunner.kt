package org.ggomes.myleo.decision.dual.data

import android.content.Context
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import java.io.File
import org.ggomes.myleo.decision.dual.domain.Accelerator

/**
 * The inference seam. [run] takes the host-side pieces: gathered fp32 embeddings, the attention
 * mask and the one-hot question type. A fake implementation is enough to test the engine's
 * sequencing and timings without a 185 MB graph.
 */
interface JuliaGraphRunner : AutoCloseable {
    val window: Int
    val accelerator: Accelerator
    fun run(embeddings: FloatArray, attentionMask: FloatArray, qtypeOnehot: FloatArray): GraphRun
}

/**
 * [JuliaGraphRunner] over LiteRT's `CompiledModel` and the `serving_default` signature.
 *
 * The GPU path explicitly requests **FP32**: the checkpoint's answers change under fp16
 * arithmetic, so no fp16 GPU precision and no NPU path is offered. Creation never silently
 * falls back — the engine decides which accelerator to try and reports the one that compiled.
 */
class LiteRtGraphRunner private constructor(
    private val model: CompiledModel,
    private val inputs: Map<String, TensorBuffer>,
    private val outputs: Map<String, TensorBuffer>,
    override val window: Int,
    override val accelerator: Accelerator,
) : JuliaGraphRunner {

    override fun run(
        embeddings: FloatArray,
        attentionMask: FloatArray,
        qtypeOnehot: FloatArray,
    ): GraphRun {
        require(embeddings.size == window * JuliaTokenTable.WIDTH) {
            "expected ${window * JuliaTokenTable.WIDTH} embedding values"
        }
        require(attentionMask.size == window) { "expected $window attention values" }

        val writeStart = System.nanoTime()
        inputs.getValue("inputs_embeds").writeFloat(embeddings)
        inputs.getValue("attention_mask").writeFloat(attentionMask)
        inputs.getValue("qtype_onehot").writeFloat(qtypeOnehot)
        val written = System.nanoTime()

        model.run(inputs, outputs, SIGNATURE)
        val enqueued = System.nanoTime()

        val tokenLogits = outputs.getValue(OUTPUT_NAME).readFloat()
        val read = System.nanoTime()

        require(tokenLogits.size == window) { "unexpected token_logits size ${tokenLogits.size}" }
        return GraphRun(
            tokenLogits = tokenLogits,
            writeMs = millis(written - writeStart),
            enqueueMs = millis(enqueued - written),
            readbackMs = millis(read - enqueued),
        )
    }

    override fun close() {
        try {
            (inputs.values + outputs.values).forEach { runCatching { it.close() } }
        } finally {
            model.close()
        }
    }

    companion object {
        const val SIGNATURE = "serving_default"
        const val OUTPUT_NAME = "token_logits"

        /** Signature input names, in the order the model declares them. */
        val INPUT_NAMES = listOf("attention_mask", "inputs_embeds", "qtype_onehot")

        fun create(
            graph: File,
            window: Int,
            accelerator: Accelerator,
            environment: Environment,
        ): LiteRtGraphRunner {
            val options =
                CompiledModel.Options(
                    when (accelerator) {
                        Accelerator.GPU -> com.google.ai.edge.litert.Accelerator.GPU
                        Accelerator.CPU -> com.google.ai.edge.litert.Accelerator.CPU
                    },
                ).apply {
                    when (accelerator) {
                        Accelerator.GPU ->
                            gpuOptions =
                                CompiledModel.GpuOptions(precision = CompiledModel.GpuOptions.Precision.FP32)
                        Accelerator.CPU -> cpuOptions = CompiledModel.CpuOptions(numThreads = 4)
                    }
                }
            val model = CompiledModel.create(graph.absolutePath, options, environment)
            val inputs = linkedMapOf<String, TensorBuffer>()
            val outputs = linkedMapOf<String, TensorBuffer>()
            try {
                INPUT_NAMES.forEach { inputs[it] = model.createInputBuffer(it, SIGNATURE) }
                outputs[OUTPUT_NAME] = model.createOutputBuffer(OUTPUT_NAME, SIGNATURE)
                return LiteRtGraphRunner(model, inputs, outputs, window, accelerator)
            } catch (failure: Throwable) {
                (inputs.values + outputs.values).forEach { runCatching { it.close() } }
                runCatching { model.close() }
                throw failure
            }
        }

        /** Wiring for the app: binds the runner to the process runtime's environment. */
        fun factory(context: Context): (Accelerator, Int, File) -> JuliaGraphRunner {
            val environment = JuliaLiteRt.environment(context)
            return { accelerator, window, graph ->
                create(graph, window, accelerator, environment)
            }
        }

        private fun millis(nanos: Long): Double = nanos / 1_000_000.0
    }
}
