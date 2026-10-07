package org.ggomes.myleo.llm.dual.data

import android.content.Context
import java.io.File

/**
 * Resolves the Phi-4-mini-instruct **int4** ONNX Runtime GenAI model. The package
 * (weights + config + tokenizer) is downloaded to `filesDir/models/` on first use.
 */
object OnnxGenAIModelLocator {
    const val FOLDER = "phi-4-mini-instruct-onnx-int4"
    const val REPO = "microsoft/Phi-4-mini-instruct-onnx"
    const val DIR = "cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4"

    const val CONFIG_FILE = "genai_config.json"
    const val WEIGHTS_FILE = "model.onnx"

    const val HINT =
        "Model not downloaded yet — tap Download (~4.9 GB, Phi-4-mini-instruct int4 from Hugging Face)."

    /**
     * One file to fetch. [name] is the repo-relative path (used for the URL); the local
     * file is saved as its basename so the runtime finds flat `model.onnx` + `.data`.
     */
    data class RemoteFile(val name: String, val repo: String = REPO) {
        val localName: String get() = name.substringAfterLast('/')
    }

    val files = listOf(
        RemoteFile("$DIR/model.onnx"),
        RemoteFile("$DIR/model.onnx.data"),
        RemoteFile("$DIR/genai_config.json"),
        RemoteFile("$DIR/tokenizer.json"),
        RemoteFile("$DIR/tokenizer_config.json"),
        RemoteFile("$DIR/vocab.json"),
        RemoteFile("$DIR/merges.txt"),
    )

    fun packageDir(context: Context): File = File(context.filesDir, "models/$FOLDER")

    fun locate(context: Context): File? {
        val dir = packageDir(context)
        val required = listOf(CONFIG_FILE, WEIGHTS_FILE, "model.onnx.data", "tokenizer.json", "tokenizer_config.json")
        return dir.takeIf { d -> required.all { File(d, it).exists() && File(d, it).length() > 0 } }
    }

    fun url(file: RemoteFile): String =
        "https://huggingface.co/${file.repo}/resolve/main/${file.name}"

    /** Total on-disk size of the downloaded package (0 when absent). */
    fun packageSize(dir: File): Long =
        dir.listFiles()?.sumOf { it.length() } ?: 0
}
