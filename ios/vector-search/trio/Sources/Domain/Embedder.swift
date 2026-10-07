import Foundation

/// Narrow inference-engine boundary for text embeddings.
///
/// Implementations wrap a runtime SDK (e.g. a LiteRT `Interpreter`) and must
/// return the L2-normalized embedding vector for `text`. Mocked in unit tests —
/// never load a real model there.
protocol Embedder {
    func embed(text: String) throws -> [Float]
}
