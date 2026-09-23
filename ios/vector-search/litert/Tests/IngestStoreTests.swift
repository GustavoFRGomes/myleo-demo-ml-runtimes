import Combine
import XCTest
@testable import MyLeoLite

@MainActor
final class IngestStoreTests: XCTestCase {
    private var bag = Set<AnyCancellable>()

    private func makeStore(
        repo: FakeEmbeddingRepository = FakeEmbeddingRepository(),
        docs: FakeDocuments = FakeDocuments(),
        battery: Int? = 80,
        loader: EmailDatasetLoader? = nil
    ) -> IngestStore {
        IngestStore(
            useCase: CalculateEmbeddingUseCase(repository: repo),
            documents: docs,
            modelLoadMs: 812,
            modelName: "embedding.tflite",
            systemStats: FakeSystemStats(percent: battery),
            datasetLoader: loader
        )
    }

    private func awaitState(
        _ store: IngestStore,
        timeout: TimeInterval = 5,
        where predicate: @escaping (IngestState) -> Bool
    ) async {
        let exp = expectation(description: "state")
        store.$state.dropFirst().sink { if predicate($0) { exp.fulfill() } }.store(in: &bag)
        await fulfillment(of: [exp], timeout: timeout)
    }

    func test_send_ingest_persistsChunksAndReportsTimings() async {
        let docs = FakeDocuments()
        let store = makeStore(docs: docs)

        await store.send(.corpusChanged("first\n\nsecond"))
        await store.send(.ingest)
        await awaitState(store) { !$0.isIngesting && $0.chunks.count == 2 }

        XCTAssertNil(store.state.error)
        XCTAssertEqual(store.state.chunks.map(\.text), ["first", "second"])
        XCTAssertTrue(store.state.stored)
        XCTAssertEqual(store.state.documents, [DocumentStat(label: "Document 1", chunks: 2)])
        XCTAssertEqual(store.state.corpus, CorpusStats(documents: 1, chunks: 2))
        XCTAssertEqual(docs.ingested["Document 1"]?.map(\.text), ["first", "second"])
        let run = try? XCTUnwrap(store.state.lastRun)
        XCTAssertEqual(run?.document, "Document 1")
        XCTAssertEqual(run?.chunks, 2)
        XCTAssertEqual(run?.batteryStartPct, 80)
        XCTAssertEqual(run?.batteryEndPct, 80)
        XCTAssertGreaterThanOrEqual(run?.totalMs ?? -1, (run?.embedMs ?? 0) + (run?.storeMs ?? 0))
        XCTAssertEqual(store.state.modelName, "embedding.tflite")
        XCTAssertEqual(store.state.modelLoadMs, 812)
    }

    func test_send_ingestBlankCorpus_doesNothing() async {
        let docs = FakeDocuments()
        let store = makeStore(docs: docs)

        await store.send(.ingest)
        try? await Task.sleep(nanoseconds: 100_000_000)

        XCTAssertFalse(store.state.isIngesting)
        XCTAssertTrue(store.state.chunks.isEmpty)
        XCTAssertTrue(docs.ingested.isEmpty)
    }

    func test_send_clearIndex_clearsStateAndStats() async {
        let docs = FakeDocuments()
        let store = makeStore(docs: docs)
        await store.send(.corpusChanged("first"))
        await store.send(.ingest)
        await awaitState(store) { !$0.isIngesting && $0.chunks.count == 1 }

        await store.send(.clearIndex)
        await awaitState(store) { $0.chunks.isEmpty && $0.corpus.chunks == 0 }

        XCTAssertFalse(store.state.stored)
        XCTAssertEqual(store.state.corpus, CorpusStats())
    }

    func test_send_ingestEnronSource_chunksLoadedEmails() async {
        let docs = FakeDocuments()
        let loader = FakeDatasetLoader(emails: ["para one\n\npara two", "single"])
        let store = makeStore(docs: docs, loader: loader)
        await store.send(.sourceChanged(.enronSample))
        await store.send(.itemCountChanged(10))
        await store.send(.ingest)
        await awaitState(store) { !$0.isIngesting && $0.chunks.count == 3 }

        XCTAssertEqual(loader.lastCount, 10)
        XCTAssertEqual(store.state.chunks.map(\.text), ["para one", "para two", "single"])
        XCTAssertTrue(store.state.stored)
        XCTAssertEqual(store.state.corpus, CorpusStats(documents: 2, chunks: 3))
        XCTAssertEqual(
            store.state.documents,
            [DocumentStat(label: "Email 1", chunks: 2), DocumentStat(label: "Email 2", chunks: 1)]
        )
        XCTAssertEqual(store.state.lastRun?.document, "Emails 1-2")
    }

    func test_send_ingestEnronSource_tracksParentEmail() async {
        let docs = FakeDocuments()
        let loader = FakeDatasetLoader(emails: ["para one\n\npara two"])
        let store = makeStore(docs: docs, loader: loader)
        await store.send(.sourceChanged(.enronSample))
        await store.send(.ingest)
        await awaitState(store) { !$0.isIngesting && $0.chunks.count == 2 }

        XCTAssertEqual(docs.lastIngested?.map(\.source), ["para one\n\npara two", "para one\n\npara two"])
    }

    func test_send_ingestEnronWithoutLoader_showsError() async {
        let store = makeStore()
        await store.send(.sourceChanged(.enronSample))
        await store.send(.ingest)
        await awaitState(store) { !$0.isIngesting }

        XCTAssertNotNil(store.state.error)
        XCTAssertTrue(store.state.chunks.isEmpty)
    }
}

final class FakeDatasetLoader: EmailDatasetLoader, @unchecked Sendable {
    var emails: [String]
    var lastCount: Int?

    init(emails: [String] = []) {
        self.emails = emails
    }

    func availableEmails() throws -> Int { emails.count }

    func loadEmails(count: Int) throws -> [String] {
        lastCount = count
        return Array(emails.prefix(count))
    }
}
