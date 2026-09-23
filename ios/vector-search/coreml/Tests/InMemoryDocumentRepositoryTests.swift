import Combine
import XCTest
@testable import MyLeoVSCoreML

final class InMemoryDocumentRepositoryTests: XCTestCase {
    func test_search_afterIngest_returnsHitsOrderedBySimilarity() throws {
        let repo = InMemoryDocumentRepository()
        try repo.ingest(document: "Document 1", chunks: [ChunkEmbedding(text: "cats", vector: hot(0))])
        try repo.ingest(document: "Document 2", chunks: [ChunkEmbedding(text: "quantum", vector: hot(1))])

        let hits = try repo.search(vector: hot(0), topK: 5)

        XCTAssertEqual(hits.map(\.text), ["cats", "quantum"])
        XCTAssertEqual(hits[0].similarity, 1.0, accuracy: 1e-6)
        XCTAssertLessThan(hits[1].similarity, hits[0].similarity)
    }

    func test_search_emptyIndex_returnsEmpty() throws {
        XCTAssertTrue(try InMemoryDocumentRepository().search(vector: hot(0)).isEmpty)
    }

    func test_stats_countsDocumentsAndChunks() throws {
        let repo = InMemoryDocumentRepository()
        try repo.ingest(document: "Document 1", chunks: [ChunkEmbedding(text: "a", vector: hot(0)), ChunkEmbedding(text: "b", vector: hot(1))])
        try repo.ingest(document: "Document 2", chunks: [ChunkEmbedding(text: "c", vector: hot(2))])

        XCTAssertEqual(try repo.stats(), CorpusStats(documents: 2, chunks: 3))
        XCTAssertEqual(
            try repo.documentStats(),
            [DocumentStat(label: "Document 1", chunks: 2), DocumentStat(label: "Document 2", chunks: 1)]
        )
    }

    func test_clear_emptiesIndex() throws {
        let repo = InMemoryDocumentRepository()
        try repo.ingest(document: "Document 1", chunks: [ChunkEmbedding(text: "a", vector: hot(0))])

        try repo.clear()

        XCTAssertTrue(try repo.search(vector: hot(0)).isEmpty)
        XCTAssertEqual(try repo.stats(), CorpusStats())
    }

    private func hot(_ indices: Int...) -> [Float] {
        var v = Array(repeating: Float(0), count: 512)
        for i in indices { v[i] = 1 }
        return v
    }
}
