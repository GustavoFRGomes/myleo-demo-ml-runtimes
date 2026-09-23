import Combine
import XCTest
@testable import MyLeoVSOnnx

@MainActor
final class SearchStoreTests: XCTestCase {
    private var bag = Set<AnyCancellable>()

    func test_send_search_publishesRankedHits() async {
        let docs = FakeDocuments()
        docs.hits = [ScoredChunk(text: "doc one", similarity: 0.9), ScoredChunk(text: "doc two", similarity: 0.7)]
        let repo = FakeEmbeddingRepository()
        repo.vector = [0.6, 0.8]
        let store = SearchStore(useCase: CalculateEmbeddingUseCase(repository: repo), documents: docs)

        await store.send(.queryChanged("hi"))
        let exp = expectation(description: "hits published")
        store.$state.dropFirst().sink { if !$0.isSearching, $0.hits.count == 2 { exp.fulfill() } }.store(in: &bag)
        await store.send(.search)
        await fulfillment(of: [exp], timeout: 5)

        XCTAssertEqual(store.state.hits.map(\.text), ["doc one", "doc two"])
        XCTAssertEqual(store.state.hits.map(\.similarity), [0.9, 0.7])
        XCTAssertNotNil(store.state.queryMs)
        XCTAssertNil(store.state.error)
        XCTAssertEqual(docs.lastQuery, [0.6, 0.8])
    }

    func test_send_searchBlankQuery_doesNothing() async {
        let store = SearchStore(
            useCase: CalculateEmbeddingUseCase(repository: FakeEmbeddingRepository()),
            documents: FakeDocuments()
        )

        await store.send(.search)
        try? await Task.sleep(nanoseconds: 100_000_000)

        XCTAssertFalse(store.state.isSearching)
        XCTAssertTrue(store.state.hits.isEmpty)
        XCTAssertNil(store.state.error)
    }

    func test_send_searchFailure_showsError() async {
        let repo = FakeEmbeddingRepository()
        repo.error = TestFailure(message: "no model")
        let store = SearchStore(useCase: CalculateEmbeddingUseCase(repository: repo), documents: FakeDocuments())

        await store.send(.queryChanged("hi"))
        let exp = expectation(description: "error published")
        store.$state.dropFirst().sink { if !$0.isSearching, $0.error != nil { exp.fulfill() } }.store(in: &bag)
        await store.send(.search)
        await fulfillment(of: [exp], timeout: 5)

        XCTAssertTrue(store.state.hits.isEmpty)
        XCTAssertNotNil(store.state.error)
    }
}
