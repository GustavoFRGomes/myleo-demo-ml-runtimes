package org.ggomes.myleo.vectorsearch.onnx.data

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/**
 * Raw ONNX Runtime inference seam. Production code uses [OrtSessionRunner];
 * unit tests use a fake. Keeps ORT (native) classes out of unit tests entirely.
 */
interface OnnxSessionRunner : AutoCloseable {
    /** Runs one `[1 x seqLen]` INT64 triple, returns flat `last_hidden_state` (`seqLen * dim`). */
    fun runHidden(ids: LongArray, mask: LongArray, typeIds: LongArray): FloatArray
}

/**
 * [OnnxSessionRunner] over an [OrtSession].
 *
 * Assumes a BERT-style sentence model with INT64 `input_ids` /
 * `attention_mask` / `token_type_ids` (`[1 x seqLen]`) in and a float
 * `last_hidden_state` (`[1 x seqLen x dim]`) out (e.g. MiniLM-L6-v2).
 */
class OrtSessionRunner(
    private val session: OrtSession,
    private val env: OrtEnvironment,
    private val seqLen: Int,
) : OnnxSessionRunner {

    override fun runHidden(ids: LongArray, mask: LongArray, typeIds: LongArray): FloatArray {
        require(ids.size == seqLen && mask.size == seqLen && typeIds.size == seqLen) {
            "expected $seqLen tokens, got ${ids.size}/${mask.size}/${typeIds.size}"
        }
        val shape = longArrayOf(1, seqLen.toLong())
        // ORT 1.30 dropped the generic (env, Object, long[]) overload; long
        // tensors with an explicit shape go through the LongBuffer overload.
        OnnxTensor.createTensor(env, java.nio.LongBuffer.wrap(ids), shape).use { idsTensor ->
            OnnxTensor.createTensor(env, java.nio.LongBuffer.wrap(mask), shape).use { maskTensor ->
                OnnxTensor.createTensor(env, java.nio.LongBuffer.wrap(typeIds), shape).use { typeTensor ->
                    session.run(
                        mapOf(
                            "input_ids" to idsTensor,
                            "attention_mask" to maskTensor,
                            "token_type_ids" to typeTensor,
                        ),
                    ).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val hidden = result[0].value as Array<Array<FloatArray>>
                        require(hidden.size == 1 && hidden[0].size == seqLen) {
                            "unexpected last_hidden_state shape ${hidden.size}x${hidden[0].size} for $seqLen tokens"
                        }
                        val dim = hidden[0][0].size
                        return FloatArray(seqLen * dim) { i -> hidden[0][i / dim][i % dim] }
                    }
                }
            }
        }
    }

    override fun close() {
        session.close()
    }
}

/** Masks-average of token vectors. Pure — unit-tested. */
internal fun meanPool(hidden: FloatArray, seqLen: Int, dim: Int, mask: IntArray): FloatArray {
    val sums = FloatArray(dim)
    var count = 0f
    for (token in 0 until seqLen) {
        if (token >= mask.size || mask[token] == 0) continue
        count += 1f
        val base = token * dim
        for (d in 0 until dim) sums[d] += hidden[base + d]
    }
    if (count == 0f) return sums
    return FloatArray(dim) { sums[it] / count }
}
