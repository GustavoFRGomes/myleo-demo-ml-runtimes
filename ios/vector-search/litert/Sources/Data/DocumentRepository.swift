import Foundation

/// Chunk text paired with its computed embedding, ready to persist.
/// `source` is the full parent item the chunk came from (email raw string,
/// or the whole free-text corpus) so hits can open their source.
struct ChunkEmbedding: Equatable {
    let text: String
    let vector: [Float]
    let source: String
}

/// A ranked search hit. `similarity` is cosine similarity in 0...1 (higher = closer).
struct ScoredChunk: Equatable {
    let text: String
    let similarity: Double
    let source: String
}

/// Live corpus totals for the performance report.
struct CorpusStats: Equatable {
    var documents: Int = 0
    var chunks: Int = 0
}

/// Per-document chunk counts for the performance report.
struct DocumentStat: Equatable {
    let label: String
    let chunks: Int
}

/// Persistence + ANN-search boundary. The Store layer talks to this only.
protocol DocumentRepository {
    /// Appends `chunks` under `document` (re-ingesting the same text duplicates it).
    func ingest(document: String, chunks: [ChunkEmbedding]) throws
    /// Deletes the whole corpus.
    func clear() throws
    /// Live totals: distinct documents + stored chunks.
    func stats() throws -> CorpusStats
    /// Chunk counts per document label, sorted by label.
    func documentStats() throws -> [DocumentStat]
    /// Top-`topK` chunks by cosine similarity to `vector`. Empty when the index is empty.
    func search(vector: [Float], topK: Int) throws -> [ScoredChunk]
}

extension DocumentRepository {
    func search(vector: [Float]) throws -> [ScoredChunk] {
        try search(vector: vector, topK: 5)
    }
}

/// `DocumentRepository` over an in-memory cosine index (F-VS-3, F-VS-4).
///
/// Runtime-idiomatic lightweight store for this leaf: exact brute-force cosine
/// search over L2-normalized vectors. A persistent index (ObjectBox Swift /
/// Core Data) plugs in behind the same protocol next.
final class InMemoryDocumentRepository: DocumentRepository {
    private struct Entry {
        let document: String
        let text: String
        let vector: [Float]
        let source: String
    }

    private var entries: [Entry] = []
    private let lock = NSLock()

    func ingest(document: String, chunks: [ChunkEmbedding]) throws {
        lock.withLock {
            entries += chunks.map { Entry(document: document, text: $0.text, vector: $0.vector, source: $0.source) }
        }
    }

    func clear() throws {
        lock.withLock { entries = [] }
    }

    func stats() throws -> CorpusStats {
        lock.withLock {
            CorpusStats(
                documents: Set(entries.map(\.document)).count,
                chunks: entries.count
            )
        }
    }

    func documentStats() throws -> [DocumentStat] {
        lock.withLock {
            Dictionary(grouping: entries, by: \.document)
                .map { DocumentStat(label: $0.key, chunks: $0.value.count) }
                .sorted { $0.label < $1.label }
        }
    }

    func search(vector: [Float], topK: Int) throws -> [ScoredChunk] {
        lock.withLock {
            entries
                .map { ScoredChunk(text: $0.text, similarity: cosineSimilarity(vector, $0.vector), source: $0.source) }
                .sorted { $0.similarity > $1.similarity }
                .prefix(topK)
                .map { $0 }
        }
    }
}

private func cosineSimilarity(_ a: [Float], _ b: [Float]) -> Double {
    guard a.count == b.count, !a.isEmpty else { return 0 }
    var dot: Double = 0
    var na: Double = 0
    var nb: Double = 0
    for i in a.indices {
        dot += Double(a[i]) * Double(b[i])
        na += Double(a[i]) * Double(a[i])
        nb += Double(b[i]) * Double(b[i])
    }
    guard na > 0, nb > 0 else { return 0 }
    return (dot / (na.squareRoot() * nb.squareRoot())).clamped(to: 0...1)
}

private extension Double {
    func clamped(to range: ClosedRange<Double>) -> Double {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
