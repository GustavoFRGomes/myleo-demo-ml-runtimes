package org.ggomes.myleo.vectorsearch.dual.data

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import org.ggomes.myleo.vectorsearch.dual.domain.Embedder

/**
 * Builds the production ONNX embedding stack from assets (model + vocab
 * bundled with the app — never downloaded at runtime, never needed in unit tests).
 *
 * Expected assets layout:
 * - `models/onnx/embedding.onnx` — MiniLM sentence model (INT64 `input_ids` /
 *   `attention_mask` / `token_type_ids` `[1 x seqLen]` in, float
 *   `last_hidden_state` `[1 x seqLen x dim]` out).
 * - `models/onnx/vocab.txt` — one WordPiece token per line.
 */
object OnnxEmbedderFactory {

    const val DEFAULT_MODEL_ASSET_PATH = "models/onnx/embedding.onnx"
    const val DEFAULT_VOCAB_ASSET_PATH = "models/onnx/vocab.txt"

    fun create(
        context: Context,
        modelAssetPath: String = DEFAULT_MODEL_ASSET_PATH,
        vocabAssetPath: String = DEFAULT_VOCAB_ASSET_PATH,
        maxSeqLen: Int = OnnxEmbedder.DEFAULT_MAX_SEQ_LEN,
    ): OnnxEmbedder {
        val modelBytes = try {
            context.assets.open(modelAssetPath).use { it.readBytes() }
        } catch (e: Exception) {
            throw IllegalStateException("cannot load ONNX model asset: $modelAssetPath", e)
        }
        val vocab = try {
            context.assets.open(vocabAssetPath).bufferedReader().use { it.readLines() }
        } catch (e: Exception) {
            throw IllegalStateException("missing vocab asset: $vocabAssetPath", e)
        }
        val env = OrtEnvironment.getEnvironment()
        val session = try {
            env.createSession(modelBytes, OrtSession.SessionOptions())
        } catch (e: Exception) {
            throw IllegalStateException("cannot create ORT session for: $modelAssetPath", e)
        }
        return OnnxEmbedder(OrtSessionRunner(session, env, maxSeqLen), WordpieceTokenizer(vocab), maxSeqLen)
    }
}

/** Convenience wiring: a usable [Embedder] straight from assets. */
fun Context.bundledOnnxEmbedder(): Embedder = OnnxEmbedderFactory.create(this)
