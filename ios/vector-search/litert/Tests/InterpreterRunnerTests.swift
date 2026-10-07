import XCTest
@testable import MyLeoLite

final class InterpreterRunnerTests: XCTestCase {
    func test_resolveInputOrder_bertNames_positionalOrder() throws {
        let order = try InterpreterRunner.resolveInputOrder(names: [
            "input_word_ids", "input_mask", "input_type_ids",
        ])

        XCTAssertEqual(order.ids, 0)
        XCTAssertEqual(order.mask, 1)
        XCTAssertEqual(order.segment, 2)
    }

    func test_resolveInputOrder_minilmNames_shuffledOrder() throws {
        let order = try InterpreterRunner.resolveInputOrder(names: [
            "serving_default_attention_mask:0",
            "serving_default_input_ids:0",
            "serving_default_token_type_ids:0",
        ])

        XCTAssertEqual(order.ids, 1)
        XCTAssertEqual(order.mask, 0)
        XCTAssertEqual(order.segment, 2)
    }

    func test_resolveInputOrder_tooFew_throws() {
        XCTAssertThrowsError(try InterpreterRunner.resolveInputOrder(names: ["a", "b"])) { error in
            XCTAssertEqual(error as? LiteRTEmbedderError, .insufficientInputs(2))
        }
    }

    func test_resolveInputOrder_unrecognized_throws() {
        XCTAssertThrowsError(
            try InterpreterRunner.resolveInputOrder(names: ["foo", "bar", "baz"])
        ) { error in
            XCTAssertEqual(
                error as? LiteRTEmbedderError,
                .unknownInputs(["foo", "bar", "baz"])
            )
        }
    }

    func test_meanPool_averagesMaskedPositions() {
        XCTAssertEqual(meanPool(hidden: [1, 0, 99, 99, 0, 4], seqLen: 3, dim: 2, mask: [1, 0, 1]), [0.5, 2.0])
    }

    func test_meanPool_allMasked_returnsZeros() {
        XCTAssertEqual(meanPool(hidden: [5, 6, 7, 8], seqLen: 2, dim: 2, mask: [0, 0]), [0, 0])
    }
}
