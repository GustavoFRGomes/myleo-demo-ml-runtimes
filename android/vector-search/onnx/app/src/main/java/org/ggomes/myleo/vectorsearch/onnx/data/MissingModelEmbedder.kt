package org.ggomes.myleo.vectorsearch.onnx.data

import org.ggomes.myleo.vectorsearch.onnx.domain.Embedder

/**
 * [Embedder] used when no model asset is bundled: every call fails with a
 * message telling the developer where to put the model. Keeps the app
 * runnable (and its error state demoable) before a `.tflite` is added.
 */
class MissingModelEmbedder(
    private val reason: String =
        "no embedding model bundled — add models/embedding.tflite + models/vocab.txt " +
            "under app/src/main/assets (see README)",
) : Embedder {
    override fun embed(text: String): List<Float> =
        throw IllegalStateException(reason)
}
