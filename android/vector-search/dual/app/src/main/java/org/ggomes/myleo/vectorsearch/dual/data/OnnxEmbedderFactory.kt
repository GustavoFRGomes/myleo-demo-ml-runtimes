package org.ggomes.myleo.vectorsearch.dual.data

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import org.ggomes.myleo.vectorsearch.dual.domain.Embedder

/** Execution provider requested for the ONNX session. */
enum class OnnxExecutionProvider(val label: String) {
    CPU("cpu"),
    WEBGPU("webgpu");

    companion object {
        /** `--es onnx_ep webgpu` on the launch intent; anything else is CPU. */
        fun fromExtra(value: String?): OnnxExecutionProvider =
            entries.firstOrNull { it.label == value?.trim()?.lowercase() } ?: CPU
    }
}

/**
 * Builds the production ONNX embedding stack from assets (model + vocab
 * bundled with the app — never downloaded at runtime, never needed in unit tests).
 *
 * Expected assets layout:
 * - `models/onnx/embedding.onnx` — MiniLM sentence model (INT64 `input_ids` /
 *   `attention_mask` / `token_type_ids` `[1 x seqLen]` in, float
 *   `last_hidden_state` `[1 x seqLen x dim]` out).
 * - `models/onnx/vocab.txt` — one WordPiece token per line.
 *
 * [provider] defaults to CPU. `WEBGPU` is attempted first, then validated with a
 * warmup inference — a session that builds but cannot run falls back to CPU, and
 * the label on the returned embedder reports which path actually ran.
 */
object OnnxEmbedderFactory {

    const val DEFAULT_MODEL_ASSET_PATH = "models/onnx/embedding.onnx"
    const val DEFAULT_VOCAB_ASSET_PATH = "models/onnx/vocab.txt"

    private const val TAG = "MyLeo-Onnx"

    fun create(
        context: Context,
        modelAssetPath: String = DEFAULT_MODEL_ASSET_PATH,
        vocabAssetPath: String = DEFAULT_VOCAB_ASSET_PATH,
        maxSeqLen: Int = OnnxEmbedder.DEFAULT_MAX_SEQ_LEN,
        provider: OnnxExecutionProvider = OnnxExecutionProvider.CPU,
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
        val tokenizer = WordpieceTokenizer(vocab)
        val env = OrtEnvironment.getEnvironment()

        if (provider == OnnxExecutionProvider.WEBGPU) {
            val accelerated = runCatching {
                val session = createSession(env, modelBytes) { it.addWebGPU(emptyMap()) }
                val embedder = OnnxEmbedder(OrtSessionRunner(session, env, maxSeqLen), tokenizer, maxSeqLen, provider.label)
                // A built session is not a working session: Dawn may still fail to
                // create a device or the EP may reject the graph at first run.
                embedder.embed("warmup")
                Log.i(TAG, "session EP=webgpu (warmup ok)")
                return embedder
            }.onFailure { Log.w(TAG, "webgpu unavailable, falling back to CPU: ${it.message}") }
            check(accelerated.isFailure)
        }

        val session = try {
            createSession(env, modelBytes) { }
        } catch (e: Exception) {
            throw IllegalStateException("cannot create ORT session for: $modelAssetPath", e)
        }
        Log.i(TAG, "session EP=cpu")
        return OnnxEmbedder(OrtSessionRunner(session, env, maxSeqLen), tokenizer, maxSeqLen, OnnxExecutionProvider.CPU.label)
    }

    private fun createSession(
        env: OrtEnvironment,
        modelBytes: ByteArray,
        configure: (OrtSession.SessionOptions) -> Unit,
    ): OrtSession {
        val options = OrtSession.SessionOptions()
        return try {
            configure(options)
            env.createSession(modelBytes, options)
        } finally {
            options.close()
        }
    }
}

/** Convenience wiring: a usable [Embedder] straight from assets. */
fun Context.bundledOnnxEmbedder(): Embedder = OnnxEmbedderFactory.create(this)
