package org.ggomes.myleo.decision.dual.data

import android.content.Context
import java.io.File

/** The resolved ONNX package: tokenizer plus the graph and its external weights side by side. */
data class OnnxFiles(
    val directory: File,
    val tokenizer: File,
    val model: File,
) {
    val weights: File get() = File(directory, OnnxModelLocator.WEIGHTS_FILE)
}

/**
 * File names, sizes and Hugging Face delivery for `SupersonicLabs/Julia-1-ONNX`
 * (revision `82a2fadf8fccfccdc5fd4e1009ba8f1a265eb7a8`). Separate folder from the LiteRT
 * package, so the picker can hold either download independently.
 */
object OnnxModelLocator {
    const val FOLDER = "julia-1-onnx"
    const val REPO = "SupersonicLabs/Julia-1-ONNX"

    const val TOKENIZER_FILE = "tokenizer.json"
    const val MODEL_FILE = "model.onnx"
    const val WEIGHTS_FILE = "model.onnx.data"

    const val TOKENIZER_BYTES = 34_363_188L
    const val MODEL_BYTES = 2_988_923L
    const val WEIGHTS_BYTES = 576_782_336L

    val REQUIRED: List<RemoteFile> =
        listOf(
            RemoteFile(TOKENIZER_FILE, TOKENIZER_BYTES, REPO),
            RemoteFile(MODEL_FILE, MODEL_BYTES, REPO),
            RemoteFile(WEIGHTS_FILE, WEIGHTS_BYTES, REPO),
        )

    val DOWNLOAD: List<RemoteFile> = REQUIRED

    /** Aggregate size of [DOWNLOAD]. */
    const val TOTAL_BYTES = 614_134_447L

    val HINT: String =
        "Julia-1 ONNX is not downloaded yet — fetch ~614 MB (model.onnx, model.onnx.data, " +
            "tokenizer.json) from Hugging Face, or push the files into files/models/$FOLDER/."

    fun directory(context: Context): File = File(context.filesDir, "models/$FOLDER")

    fun locate(directory: File): OnnxFiles? {
        if (!REQUIRED.all { File(directory, it.name).isFile }) return null
        return OnnxFiles(
            directory = directory,
            tokenizer = File(directory, TOKENIZER_FILE),
            model = File(directory, MODEL_FILE),
        )
    }

    fun locate(context: Context): OnnxFiles? = locate(directory(context))
}
