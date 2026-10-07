package org.ggomes.myleo.vectorsearch.dual.data

import org.ggomes.myleo.vectorsearch.dual.domain.Embedder
import org.ggomes.myleo.vectorsearch.dual.domain.InferenceRuntime

/**
 * [Embedder] used when no model asset is bundled: every call fails with a
 * message telling the developer where to put the model. Keeps the app
 * runnable (and its error state demoable) before a model is added.
 */
class MissingModelEmbedder(
    private val reason: String =
        "no embedding model bundled — add ${InferenceRuntime.LITERT.modelFile} under " +
            "app/src/main/assets/models/litert (or ${InferenceRuntime.ONNX.modelFile} under " +
            "app/src/main/assets/models/onnx) — see README",
) : Embedder {
    override fun embed(text: String): List<Float> =
        throw IllegalStateException(reason)
}
