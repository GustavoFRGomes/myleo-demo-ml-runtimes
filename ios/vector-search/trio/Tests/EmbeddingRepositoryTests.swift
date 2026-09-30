import XCTest
@testable import MyLeoVSTrio

final class EmbeddingRepositoryTests: XCTestCase {
    func test_embedText_validInput_returnsEmbedderVector() throws {
        let embedder = FakeEmbedder()
        embedder.vector = [0.5, -0.5]
        let repository = DefaultEmbeddingRepository(embedder: embedder)

        let result = try repository.embedText("hello")

        XCTAssertEqual(result, [0.5, -0.5])
        XCTAssertEqual(embedder.lastInput, "hello")
    }

    func test_embedText_embedderThrows_propagates() {
        let embedder = FakeEmbedder()
        embedder.error = TestFailure(message: "no model loaded")
        let repository = DefaultEmbeddingRepository(embedder: embedder)

        XCTAssertThrowsError(try repository.embedText("boom")) { error in
            XCTAssertEqual(error as? TestFailure, TestFailure(message: "no model loaded"))
        }
    }
}
