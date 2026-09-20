package org.ggomes.myleolite.data

import android.content.Context
import org.ggomes.myleolite.domain.Embedder
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Builds the production embedding stack from assets (model + vocab bundled
 * with the app — never downloaded at runtime, never needed in unit tests).
 *
 * Expected assets layout:
 * - `models/embedding.tflite` — BERT WordPiece embedding model
 *   (INT32 `ids`/`mask`/`segment_ids` in, float embedding out).
 * - `models/vocab.txt` — one WordPiece token per line.
 */
object LiteRtEmbedderFactory {

    const val DEFAULT_MODEL_ASSET_PATH = "models/embedding.tflite"
    const val DEFAULT_VOCAB_ASSET_PATH = "models/vocab.txt"

    fun create(
        context: Context,
        modelAssetPath: String = DEFAULT_MODEL_ASSET_PATH,
        vocabAssetPath: String = DEFAULT_VOCAB_ASSET_PATH,
        maxSeqLen: Int = LiteRtEmbedder.DEFAULT_MAX_SEQ_LEN,
        numThreads: Int? = null,
    ): LiteRtEmbedder {
        val options = Interpreter.Options().apply {
            if (numThreads != null) setNumThreads(numThreads)
        }
        val interpreter = try {
            Interpreter(loadModelFile(context, modelAssetPath), options)
        } catch (e: Exception) {
            throw IllegalStateException("cannot load LiteRT model asset: $modelAssetPath", e)
        }
        val vocab = try {
            context.assets.open(vocabAssetPath).bufferedReader().use { it.readLines() }
        } catch (e: Exception) {
            interpreter.close()
            throw IllegalStateException("missing vocab asset: $vocabAssetPath", e)
        }
        return LiteRtEmbedder(InterpreterRunner(interpreter), WordpieceTokenizer(vocab), maxSeqLen)
    }

    private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
        val fd = context.assets.openFd(assetPath)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength,
        )
    }
}

/** Convenience wiring: a usable [Embedder] straight from assets. */
fun Context.bundledEmbedder(): Embedder = LiteRtEmbedderFactory.create(this)
