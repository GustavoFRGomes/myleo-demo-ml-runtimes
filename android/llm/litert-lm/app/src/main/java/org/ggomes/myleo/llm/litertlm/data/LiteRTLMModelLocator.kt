package org.ggomes.myleo.llm.litertlm.data

import android.content.Context
import java.io.File

/**
 * Resolves the MiniCPM5-1B `.litertlm` model. The file is large (~793 MB) and
 * never bundled, so it is downloaded to `filesDir/models/` on first use.
 */
object LiteRTLMModelLocator {
    const val FILE_NAME = "minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm"
    const val REPO = "litert-community/MiniCPM5-1B"

    const val HINT =
        "Model not downloaded yet — tap Download (~793 MB, MiniCPM5-1B from Hugging Face)."

    fun modelsDir(context: Context): File = File(context.filesDir, "models")

    fun modelFile(context: Context): File = File(modelsDir(context), FILE_NAME)

    fun locate(context: Context): File? =
        modelFile(context).takeIf { it.exists() && it.length() > 0 }

    fun remoteUrl(): String = "https://huggingface.co/$REPO/resolve/main/$FILE_NAME"
}
