package org.ggomes.myleo.llm.dual.data

import android.content.Context
import java.io.File

/**
 * Resolves the Phi-4-mini-instruct `.litertlm` model. The file is large (~3.9 GB)
 * and never bundled, so it is downloaded to `filesDir/models/` on first use.
 */
object LiteRTLMModelLocator {
    const val FILE_NAME = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm"
    const val REPO = "litert-community/Phi-4-mini-instruct"

    const val HINT =
        "Model not downloaded yet — tap Download (~3.9 GB, Phi-4-mini-instruct from Hugging Face)."

    fun modelsDir(context: Context): File = File(context.filesDir, "models")

    fun modelFile(context: Context): File = File(modelsDir(context), FILE_NAME)

    fun locate(context: Context): File? =
        modelFile(context).takeIf { it.exists() && it.length() > 0 }

    fun remoteUrl(): String = "https://huggingface.co/$REPO/resolve/main/$FILE_NAME"
}
