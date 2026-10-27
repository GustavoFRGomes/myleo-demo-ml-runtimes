import XCTest
@testable import MyLeoDecisionOnnx

/// Synthetic encoder: one id per unicode scalar, offset so ids avoid the special tokens.
private struct CharEncoder: JuliaEncoding {
    func encode(_ text: String) -> [Int] {
        text.unicodeScalars.map { Int($0.value) + 100 }
    }
}

final class JuliaSequenceBuilderTests: XCTestCase {
    private func question(_ type: QuestionType = .choice,
                          options: [DecisionOption] = [
                            try! DecisionOption(id: "a", text: "alpha"),
                            try! DecisionOption(id: "b", text: "beta"),
                          ]) throws -> DecisionQuestion {
        try DecisionQuestion(type: type, instructions: "Pick one", options: options)
    }

    func testBuild_startsWithBos_endsWithEos_marksEachOption() throws {
        let builder = try JuliaSequenceBuilder(encoder: CharEncoder(), window: 512)
        let sequence = try builder.build(state: "the state", question: question())

        XCTAssertEqual(sequence.qtype, 0)
        XCTAssertEqual(sequence.ids.first, JuliaSequenceBuilder.cls)
        XCTAssertEqual(sequence.ids.last, JuliaSequenceBuilder.sep)
        XCTAssertEqual(sequence.markers.count, 2)
        XCTAssertEqual(sequence.ids[sequence.markers[0]], JuliaSequenceBuilder.mask)
        XCTAssertEqual(sequence.ids[sequence.markers[1]], JuliaSequenceBuilder.mask)
        // The eos between question and options sits right before the first marker.
        XCTAssertEqual(sequence.ids[sequence.markers[0] - 1], JuliaSequenceBuilder.sep)
        XCTAssertTrue(sequence.ids.contains(JuliaSequenceBuilder.mask))
    }

    func testBuild_headText_usesWireType() throws {
        let builder = try JuliaSequenceBuilder(encoder: CharEncoder(), window: 512)
        let ids = try builder.build(state: "s", question: question(.score)).ids
        let expected = CharEncoder().encode("score question: Pick one")
        XCTAssertEqual(Array(ids[1..<(1 + expected.count)]), expected)
        XCTAssertEqual(try builder.build(state: "s", question: question(.score)).qtype, 1)
        XCTAssertEqual(try builder.build(state: "s", question: .noul(instructions: "Is it?")).qtype, 2)
    }

    func testBuild_stateOverWindow_throwsInsteadOfTruncating() throws {
        let builder = try JuliaSequenceBuilder(encoder: CharEncoder(), window: 64)
        XCTAssertThrowsError(try builder.build(state: String(repeating: "x", count: 200), question: question())) { error in
            guard case DecisionError.encoding = error else {
                return XCTFail("expected encoding error, got \(error)")
            }
        }
    }

    func testBuild_reservedMaskLiteral_throws() throws {
        let builder = try JuliaSequenceBuilder(encoder: CharEncoder(), window: 512)
        XCTAssertThrowsError(try builder.build(state: "has <mask> inside", question: question()))
    }

    func testBuild_optionOver48Tokens_throws() throws {
        let long = String(repeating: "y", count: JuliaSequenceBuilder.maxOptionTokens + 1)
        let options = [
            try DecisionOption(id: "a", text: long),
            try DecisionOption(id: "b", text: "beta"),
        ]
        let builder = try JuliaSequenceBuilder(encoder: CharEncoder(), window: 512)
        XCTAssertThrowsError(try builder.build(state: "s", question: question(options: options)))
    }
}
