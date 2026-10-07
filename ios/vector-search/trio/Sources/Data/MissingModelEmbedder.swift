import Foundation

/// `Embedder` used when no model is bundled: every call throws with a message
/// telling the developer where to put the model. Keeps the app runnable (and
/// its error state demoable) before a `.tflite` is added.
struct MissingModelEmbedder: Embedder {
    let reason: String

    init(reason: String = "no embedding model bundled — add embedding.tflite + vocab.txt to the app bundle (see README)") {
        self.reason = reason
    }

    func embed(text: String) throws -> [Float] {
        throw LiteRTEmbedderError.missingModel(reason)
    }
}
