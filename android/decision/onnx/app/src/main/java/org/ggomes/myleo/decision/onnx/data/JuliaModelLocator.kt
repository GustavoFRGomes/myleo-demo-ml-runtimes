package org.ggomes.myleo.decision.onnx.data

import android.content.Context
import java.io.File

/** One file the app must fetch, with the exact size published in the model repo. */
data class RemoteFile(val name: String, val bytes: Long) {
    val url: String get() = JuliaModelLocator.remoteUrl(name)
}

/** The resolved on-disk package: tokenizer plus the ONNX graph and its external weights. */
data class JuliaModelFiles(
    val directory: File,
    val tokenizer: File,
    val model: File,
) {
    /** ORT resolves `model.onnx.data` relative to the graph, so both must sit side by side. */
    val weights: File get() = File(directory, JuliaModelLocator.WEIGHTS_FILE)
}

/**
 * File names, sizes and Hugging Face delivery for `SupersonicLabs/Julia-1-ONNX`
 * (revision `82a2fadf8fccfccdc5fd4e1009ba8f1a265eb7a8`). Models are never committed; the app
 * downloads them into its own `filesDir`.
 *
 * Sizes are part of the contract: a truncated `model.onnx.data` would fail deep inside ORT, so
 * the download checks each file's length before reporting success.
 */
object JuliaModelLocator {
    const val FOLDER = "julia-1-onnx"
    const val REPO = "SupersonicLabs/Julia-1-ONNX"

    const val TOKENIZER_FILE = "tokenizer.json"
    const val MODEL_FILE = "model.onnx"
    const val WEIGHTS_FILE = "model.onnx.data"

    const val TOKENIZER_BYTES = 34_363_188L
    const val MODEL_BYTES = 2_988_923L
    const val WEIGHTS_BYTES = 576_782_336L

    /** The package is one graph + its external data + the unchanged tokenizer. */
    val REQUIRED: List<RemoteFile> =
        listOf(
            RemoteFile(TOKENIZER_FILE, TOKENIZER_BYTES),
            RemoteFile(MODEL_FILE, MODEL_BYTES),
            RemoteFile(WEIGHTS_FILE, WEIGHTS_BYTES),
        )

    /** The whole download; there is no optional second graph in the ONNX export. */
    val DOWNLOAD: List<RemoteFile> = REQUIRED

    /** Aggregate size of [DOWNLOAD]. */
    const val TOTAL_BYTES = 614_134_447L

    val HINT: String =
        "Julia-1 ONNX is not downloaded yet — fetch ~614 MB (model.onnx, model.onnx.data, " +
            "tokenizer.json) from Hugging Face, or push the files into files/models/$FOLDER/."

    fun directory(context: Context): File = File(context.filesDir, "models/$FOLDER")

    fun remoteUrl(name: String): String = "https://huggingface.co/$REPO/resolve/main/$name"

    /** Package for a directory when every required file is present, else null. */
    fun locate(directory: File): JuliaModelFiles? {
        if (!REQUIRED.all { File(directory, it.name).isFile }) return null
        return JuliaModelFiles(
            directory = directory,
            tokenizer = File(directory, TOKENIZER_FILE),
            model = File(directory, MODEL_FILE),
        )
    }

    fun locate(context: Context): JuliaModelFiles? = locate(directory(context))
}
