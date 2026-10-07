package org.ggomes.myleo.llm.dual.data

import java.io.File

/**
 * Downloads the model's file(s), reporting aggregate byte progress, and returns
 * the resolved model path. Single-file (LiteRT) or multi-file (ONNX) — the
 * ViewModel only sees this seam.
 */
interface ModelDownloader {
    suspend fun download(onProgress: (received: Long, total: Long) -> Unit): File
}
