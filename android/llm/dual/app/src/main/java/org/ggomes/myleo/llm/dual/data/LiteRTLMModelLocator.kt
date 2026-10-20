package org.ggomes.myleo.llm.dual.data

import android.content.Context
import java.io.File

/**
 * Resolves the MiniCPM5-1B `.litertlm` model. The file is ~793 MB and never
 * bundled, so it is downloaded to `filesDir/models/` on first use.
 *
 * The bundle is the mixed INT4-block32/INT8 W4 build with FP32 activations,
 * GPU-optimized (`_gpu_opt`). It carries MiniCPM5's LiteRT-LM config and needs
 * LiteRT-LM ≥ 0.18.0 — hence the pin in `gradle/libs.versions.toml`.
 */
object LiteRTLMModelLocator {
    const val FILE_NAME = "minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm"
    const val REPO = "litert-community/MiniCPM5-1B"

    const val HINT =
        "Model not downloaded yet — tap Download (~793 MB, MiniCPM5-1B W4 from Hugging Face)."

    fun modelsDir(context: Context): File = File(context.filesDir, "models")

    fun modelFile(context: Context): File = File(modelsDir(context), FILE_NAME)

    fun locate(context: Context): File? =
        modelFile(context).takeIf { it.exists() && it.length() > 0 }

    fun remoteUrl(): String = "https://huggingface.co/$REPO/resolve/main/$FILE_NAME"
}
