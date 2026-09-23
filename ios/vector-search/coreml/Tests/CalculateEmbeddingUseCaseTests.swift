import XCTest
@testable import MyLeoVSCoreML

final class FakeEmbedder: Embedder {
    var vector: [Float] = []
    var error: Error?
    var lastInput: String?

    func embed(text: String) throws -> [Float] {
        lastInput = text
        if let error { throw error }
        return vector
    }
}

final class FakeEmbeddingRepository: EmbeddingRepository {
    var vector: [Float] = []
    var error: Error?
    var lastInput: String?

    func embedText(_ text: String) throws -> [Float] {
        lastInput = text
        if let error { throw error }
        return vector
    }
}

struct TestFailure: Error, Equatable {
    let message: String
}

final class CalculateEmbeddingUseCaseTests: XCTestCase {
    func test_invoke_blankText_throwsEmptyQuery() {
        let useCase = CalculateEmbeddingUseCase(repository: FakeEmbeddingRepository())

        XCTAssertThrowsError(try useCase("   ")) { error in
            XCTAssertEqual(error as? EmbeddingUseCaseError, .emptyQuery)
        }
    }

    func test_invoke_blankText_doesNotCallRepository() throws {
        let repository = FakeEmbeddingRepository()
        let useCase = CalculateEmbeddingUseCase(repository: repository)

        _ = try? useCase("")

        XCTAssertNil(repository.lastInput)
    }

    func test_invoke_validText_returnsRepositoryVector() throws {
        let repository = FakeEmbeddingRepository()
        repository.vector = [0.1, 0.2, 0.3]
        let useCase = CalculateEmbeddingUseCase(repository: repository)

        let result = try useCase("hello")

        XCTAssertEqual(result, [0.1, 0.2, 0.3])
        XCTAssertEqual(repository.lastInput, "hello")
    }

    func test_invoke_repositoryFailure_propagates() {
        let repository = FakeEmbeddingRepository()
        repository.error = TestFailure(message: "engine exploded")
        let useCase = CalculateEmbeddingUseCase(repository: repository)

        XCTAssertThrowsError(try useCase("hello")) { error in
            XCTAssertEqual(error as? TestFailure, TestFailure(message: "engine exploded"))
        }
    }

    func test_invoke_validText_delegatesToEmbedder() throws {
        let embedder = FakeEmbedder()
        embedder.vector = [1.0]
        let repository = DefaultEmbeddingRepository(embedder: embedder)
        let useCase = CalculateEmbeddingUseCase(repository: repository)

        let result = try useCase("hi")

        XCTAssertEqual(result, [1.0])
        XCTAssertEqual(embedder.lastInput, "hi")
    }
}
