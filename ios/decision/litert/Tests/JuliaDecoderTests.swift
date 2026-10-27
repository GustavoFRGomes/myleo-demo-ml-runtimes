import XCTest
@testable import MyLeoDecisionLiteRT

final class JuliaDecoderTests: XCTestCase {
    private func question(_ type: QuestionType) throws -> DecisionQuestion {
        switch type {
        case .choice:
            return try .choice(instructions: "Pick", options: [("a", "alpha"), ("b", "beta")])
        case .score:
            return try .score(instructions: "Rate", rubric: ["none", "some", "a lot"])
        case .noul:
            return try .noul(instructions: "Is it on fire?")
        }
    }

    func testSoftmax_sumsToOne_andPreservesOrder() throws {
        let probabilities = try JuliaDecoder.softmax([1.0, 2.0, 3.0])
        XCTAssertEqual(probabilities.count, 3)
        XCTAssertEqual(probabilities.reduce(0, +), 1.0, accuracy: 1e-12)
        XCTAssertLessThan(probabilities[0], probabilities[1])
        XCTAssertLessThan(probabilities[1], probabilities[2])
    }

    func testSoftmax_nonfinite_throws() {
        XCTAssertThrowsError(try JuliaDecoder.softmax([1.0, .infinity]))
        XCTAssertThrowsError(try JuliaDecoder.softmax([]))
    }

    func testGather_readsMarkerPositionsAndRejectsOutOfRange() throws {
        let logits: [Float] = [0, 1, 2, 3, 4]
        XCTAssertEqual(try JuliaDecoder.gather(tokenLogits: logits, markers: [1, 3]), [1, 3])
        XCTAssertThrowsError(try JuliaDecoder.gather(tokenLogits: logits, markers: [5]))
    }

    func testDecode_choice_returnsWinningId() throws {
        let decoded = try JuliaDecoder.decode(markerLogits: [0.2, 3.1], question: try question(.choice))
        XCTAssertEqual(decoded.choiceId, "b")
        XCTAssertEqual(decoded.argmax, 1)
        XCTAssertNil(decoded.score)
        XCTAssertNil(decoded.noul)
        XCTAssertEqual(decoded.maxProbability ?? 0, decoded.probabilities[1], accuracy: 1e-12)
    }

    func testDecode_score_isExpectedZeroBasedIndex() throws {
        // Probabilities 0.25 / 0.5 / 0.25 → expectation 1.0.
        let decoded = try JuliaDecoder.decode(markerLogits: [0, 1, 0], question: try question(.score))
        XCTAssertNotNil(decoded.score)
        XCTAssertEqual(decoded.score ?? -1, 1.0, accuracy: 1e-12)
        XCTAssertNil(decoded.choiceId)
    }

    func testDecode_noul_readsPTrueFromSecondOption() throws {
        let decoded = try JuliaDecoder.decode(markerLogits: [-2, 2], question: try question(.noul))
        XCTAssertEqual(decoded.noul ?? 0, decoded.probabilities[1], accuracy: 1e-12)
        XCTAssertGreaterThan(decoded.noul ?? 0, 0.9)
        XCTAssertNil(decoded.maxProbability)
    }

    func testDecode_countMismatch_throws() throws {
        XCTAssertThrowsError(try JuliaDecoder.decode(markerLogits: [1], question: try question(.choice)))
    }
}
