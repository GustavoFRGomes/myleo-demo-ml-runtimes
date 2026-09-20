package org.ggomes.myleolite.data

import org.tensorflow.lite.Interpreter

/**
 * [InferenceRunner] over a raw LiteRT [Interpreter] (the `litert` AAR's
 * `org.tensorflow.lite` binding over `libLiteRt.so`).
 *
 * Assumes a BERT-style embedding model whose three INT32 inputs are `ids`,
 * `mask`, `segment_ids` (`[1 x seqLen]`) and whose first output is the float
 * embedding vector (`[1 x N]`). Confirm the layout of the bundled model with
 * Model Explorer before changing these assumptions.
 */
class InterpreterRunner(private val interpreter: Interpreter) : InferenceRunner {

    override fun runEmbedding(ids: IntArray, mask: IntArray, segmentIds: IntArray): FloatArray {
        require(interpreter.inputTensorCount >= 3) {
            "expected at least 3 inputs (ids, mask, segment_ids), " +
                "got ${interpreter.inputTensorCount}"
        }
        val embeddingSize = interpreter.getOutputTensor(0).shape().last()
        val inputs: Array<Any> = arrayOf(
            arrayOf(ids),
            arrayOf(mask),
            arrayOf(segmentIds),
        )
        val output = arrayOf(FloatArray(embeddingSize))
        val outputs: MutableMap<Int, Any> = mutableMapOf(0 to output)
        interpreter.runForMultipleInputsOutputs(inputs, outputs)
        return output[0]
    }

    override fun close() = interpreter.close()
}
