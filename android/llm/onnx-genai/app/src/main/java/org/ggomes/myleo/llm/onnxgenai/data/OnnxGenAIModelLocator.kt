package org.ggomes.myleo.llm.onnxgenai.data

import android.content.Context
import java.io.File

/**
 * Resolves the MiniCPM5-1B ONNX Runtime GenAI package.
 *
 * This is the `onnxruntime-genai` **model builder** export (int4 `MatMulNBits` weights with
 * fp32 activations and fused `GroupQueryAttention`), not the generic fp16 export: the fp16
 * graph computes `repeat_kv` outside the attention op and ONNX Runtime sizes its decode
 * buffers from the prefill shape, so it throws `Shape mismatch attempting to re-use buffer`
 * on the first generated token with ORT 1.30/1.31.
 *
 * The four packed files live at the repository root and are downloaded flat into
 * `filesDir/models/minicpm5-1b-onnx-int4/`. `genai_config.json` is not published; it is
 * synthesized by [MiniCPM5GenAIConfig] once the weights are in place.
 */
object OnnxGenAIModelLocator {
    const val FOLDER = "minicpm5-1b-onnx-int4"
    const val REPO = "anydaytv/minicpm5-1b-onnx-int4"

    const val CONFIG_FILE = MiniCPM5GenAIConfig.FILE_NAME
    const val WEIGHTS_FILE = "model.onnx"
    const val WEIGHTS_DATA_FILE = "model.onnx.data"
    const val TOKENIZER_FILE = "tokenizer.json"
    const val TOKENIZER_CONFIG_FILE = "tokenizer_config.json"

    const val HINT =
        "Model not downloaded yet — tap Download (~1.4 GB, MiniCPM5-1B int4 from Hugging Face)."

    /**
     * One file to fetch. [name] is the repo-relative path (used for the URL); the local
     * file is saved as its basename so the runtime finds flat `model.onnx` + `.data`.
     */
    data class RemoteFile(val name: String, val repo: String = REPO) {
        val localName: String get() = name.substringAfterLast('/')
    }

    val files = listOf(
        RemoteFile(WEIGHTS_FILE),
        RemoteFile(WEIGHTS_DATA_FILE),
        RemoteFile(TOKENIZER_FILE),
        RemoteFile(TOKENIZER_CONFIG_FILE),
    )

    /** Files the downloader must place on disk. The config is derived, not downloaded. */
    val requiredFiles = files.map { it.localName }

    fun packageDir(context: Context): File = File(context.filesDir, "models/$FOLDER")

    /**
     * The package directory once every downloaded file is present. `genai_config.json` is
     * not checked here: it is synthesized by [MiniCPM5GenAIConfig] (by the downloader after
     * a fetch, and again by the engine before loading).
     */
    fun locate(context: Context): File? {
        val dir = packageDir(context)
        return dir.takeIf { d ->
            requiredFiles.all { File(d, it).exists() && File(d, it).length() > 0 }
        }
    }

    fun url(file: RemoteFile): String =
        "https://huggingface.co/${file.repo}/resolve/main/${file.name}"

    /** Total on-disk size of the downloaded package (0 when absent). */
    fun packageSize(dir: File): Long =
        dir.listFiles()?.sumOf { it.length() } ?: 0
}
