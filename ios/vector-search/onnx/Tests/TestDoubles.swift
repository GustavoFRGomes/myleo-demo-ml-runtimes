import XCTest
@testable import MyLeoVSOnnx

final class FakeDocuments: DocumentRepository {
    var hits: [ScoredChunk] = []
    var error: Error?
    var ingested: [String: [ChunkEmbedding]] = [:]
    var lastQuery: [Float]?

    var lastIngested: [ChunkEmbedding]? {
        ingested.values.first
    }

    func ingest(document: String, chunks: [ChunkEmbedding]) throws {
        if let error { throw error }
        ingested[document] = chunks
    }

    func clear() throws {
        ingested = [:]
    }

    func stats() throws -> CorpusStats {
        CorpusStats(documents: ingested.count, chunks: ingested.values.reduce(0) { $0 + $1.count })
    }

    func documentStats() throws -> [DocumentStat] {
        ingested.map { DocumentStat(label: $0.key, chunks: $0.value.count) }.sorted { $0.label < $1.label }
    }

    func search(vector: [Float], topK: Int) throws -> [ScoredChunk] {
        lastQuery = vector
        if let error { throw error }
        return hits
    }
}

struct FakeSystemStats: SystemStatsProvider {
    let percent: Int?
    func snapshot() -> DeviceSnapshot {
        DeviceSnapshot(manufacturer: "TestMan", model: "TestModel", onBattery: true, batteryPercent: percent)
    }
}
