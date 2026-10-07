import Foundation

/// Repository boundary for embedding access. The View/Intent layer talks to
/// this protocol only — never to the inference SDK directly.
protocol EmbeddingRepository {
    func embedText(_ text: String) throws -> [Float]
}

/// Delegates to the injected `Embedder`.
final class DefaultEmbeddingRepository: EmbeddingRepository {
    private let embedder: Embedder

    init(embedder: Embedder) {
        self.embedder = embedder
    }

    func embedText(_ text: String) throws -> [Float] {
        try embedder.embed(text: text)
    }
}
