import XCTest
@testable import MyLeoVSTrio

final class FakeRunner: InferenceRunner {
    var vector: [Float] = [1]
    var lastIds: [Int32]?
    var lastMask: [Int32]?
    var lastSegmentIds: [Int32]?

    func runEmbedding(ids: [Int32], mask: [Int32], segmentIds: [Int32]) throws -> [Float] {
        lastIds = ids
        lastMask = mask
        lastSegmentIds = segmentIds
        return vector
    }
}

struct FakeTokenizer: TextTokenizer {
    func tokenize(text: String, maxSeqLen: Int) -> TokenizedInput {
        TokenizedInput(ids: [101, 102], mask: [1, 1], segmentIds: [0, 0])
    }
}

final class LiteRTEmbedderTests: XCTestCase {
    func test_embed_validText_returnsL2NormalizedVector() throws {
        let runner = FakeRunner()
        runner.vector = [3, 4]
        let embedder = LiteRTEmbedder(runner: runner, tokenizer: FakeTokenizer())

        let result = try embedder.embed(text: "hello")

        let norm = sqrt(result.reduce(0) { $0 + $1 * $1 })
        XCTAssertEqual(norm, 1, accuracy: 1e-6)
        XCTAssertEqual(result[0], 0.6, accuracy: 1e-6)
        XCTAssertEqual(result[1], 0.8, accuracy: 1e-6)
    }

    func test_embed_validText_forwardsTokenArraysToRunner() throws {
        let runner = FakeRunner()
        let embedder = LiteRTEmbedder(runner: runner, tokenizer: FakeTokenizer())

        _ = try embedder.embed(text: "hi")

        XCTAssertEqual(runner.lastIds, [101, 102])
        XCTAssertEqual(runner.lastMask, [1, 1])
        XCTAssertEqual(runner.lastSegmentIds, [0, 0])
    }

    func test_embed_zeroVector_returnsWithoutNaN() throws {
        let runner = FakeRunner()
        runner.vector = [0, 0]
        let embedder = LiteRTEmbedder(runner: runner, tokenizer: FakeTokenizer())

        XCTAssertEqual(try embedder.embed(text: "hi"), [0, 0])
    }

    func test_l2Normalize_unitVector_returnsUnchanged() {
        XCTAssertEqual(l2Normalize([1, 0]), [1, 0])
    }

    func test_l2Normalize_zeroVector_returnsUnchanged() {
        XCTAssertEqual(l2Normalize([0, 0]), [0, 0])
    }
}
