import Foundation

/// Tiny stub repositories for SwiftUI previews (never used in production or tests).
final class PreviewEmbeddingRepository: EmbeddingRepository {
    func embedText(_ text: String) throws -> [Float] { [0.6, 0.8] }
}

final class PreviewDocumentRepository: DocumentRepository {
    func ingest(document: String, chunks: [ChunkEmbedding]) throws {}
    func clear() throws {}
    func stats() throws -> CorpusStats { CorpusStats(documents: 1, chunks: 2) }
    func documentStats() throws -> [DocumentStat] { [DocumentStat(label: "Document 1", chunks: 2)] }
    func search(vector: [Float], topK: Int) throws -> [ScoredChunk] {
        [ScoredChunk(text: "preview hit", similarity: 0.87, source: "preview source")]
    }
}

enum PreviewRepositories {
    static var embedding: EmbeddingRepository { PreviewEmbeddingRepository() }
    static var documents: DocumentRepository { PreviewDocumentRepository() }
}

struct PreviewDatasetLoader: EmailDatasetLoader {
    func availableEmails() throws -> Int { 2 }
    func loadEmails(count: Int) throws -> [String] {
        Array(repeating: "preview email body", count: min(count, 2))
    }
}
