package org.ggomes.myleo.llm.onnxgenai.data

import android.content.Context
import java.io.File

/**
 * Resolves the MiniCPM5-1B **int4** ONNX Runtime GenAI model. The package
 * (weights + config + tokenizer) is downloaded to `filesDir/models/` on first
 * use — same files as the iOS `onnx-genai` leaf.
 */
object OnnxGenAIModelLocator {
    const val FOLDER = "minicpm5-1b-onnx-int4"
    const val CONFIG_FILE = "genai_config.json"
    const val WEIGHTS_FILE = "model.onnx"

    const val HINT =
        "Model not downloaded yet — tap Download (~1.42 GB, MiniCPM5-1B int4 for ONNX Runtime GenAI)."

    /** One file to fetch: its name (repo-relative) and the Hugging Face repo. */
    data class RemoteFile(val name: String, val repo: String)

    val files = listOf(
        RemoteFile("model.onnx", "anydaytv/minicpm5-1b-onnx-int4"),
        RemoteFile("model.onnx.data", "anydaytv/minicpm5-1b-onnx-int4"),
        RemoteFile("tokenizer.json", "anydaytv/minicpm5-1b-onnx-int4"),
        RemoteFile("tokenizer_config.json", "anydaytv/minicpm5-1b-onnx-int4"),
        // The int4 repo omits these two; take them from the same base model.
        RemoteFile("genai_config.json", "onnx-community/MiniCPM5-1B"),
        RemoteFile("chat_template.jinja", "onnx-community/MiniCPM5-1B"),
    )

    fun packageDir(context: Context): File = File(context.filesDir, "models/$FOLDER")

    fun locate(context: Context): File? {
        val dir = packageDir(context)
        val required = listOf(CONFIG_FILE, WEIGHTS_FILE, "tokenizer.json", "tokenizer_config.json")
        return dir.takeIf { d -> required.all { File(d, it).exists() && File(d, it).length() > 0 } }
    }

    fun url(file: RemoteFile): String =
        "https://huggingface.co/${file.repo}/resolve/main/${file.name}"

    /** Total on-disk size of the downloaded package (0 when absent). */
    fun packageSize(dir: File): Long =
        dir.listFiles()?.sumOf { it.length() } ?: 0
}
