import XCTest
@testable import MyLeoDecisionLiteRT

final class DecisionModelsTests: XCTestCase {
    func testChoice_preservesOrderAndIds() throws {
        let question = try DecisionQuestion.choice(
            instructions: "Route it",
            options: [("billing", "Billing issue"), ("tech", "Technical issue")]
        )
        XCTAssertEqual(question.type, .choice)
        XCTAssertEqual(question.options.map(\.id), ["billing", "tech"])
        XCTAssertEqual(question.options[1].text, "Technical issue")
    }

    func testScore_idsAreZeroBasedIndices() throws {
        let question = try DecisionQuestion.score(instructions: "Rate", rubric: ["low", "mid", "high"])
        XCTAssertEqual(question.options.map(\.id), ["0", "1", "2"])
        XCTAssertEqual(question.type.qtype, 1)
    }

    func testNoul_defaultsToLiteralFalseTrue() throws {
        let question = try DecisionQuestion.noul(instructions: "Is it safe?")
        XCTAssertEqual(question.options.map(\.id), ["false", "true"])
        XCTAssertEqual(question.options.map(\.text), ["false", "true"])
        XCTAssertEqual(question.type.qtype, 2)
    }

    func testNoul_wrongOrder_isRejected() throws {
        XCTAssertThrowsError(try DecisionQuestion(
            type: .noul,
            instructions: "Is it safe?",
            options: [
                try DecisionOption(id: "true", text: "yes"),
                try DecisionOption(id: "false", text: "no"),
            ]
        ))
    }

    func testOptionCount_mustBeTwoToTwenty() throws {
        let one = [try DecisionOption(id: "a", text: "A")]
        XCTAssertThrowsError(try DecisionQuestion(type: .choice, instructions: "Pick", options: one))

        let many = (0...20).map { try! DecisionOption(id: "\($0)", text: "option \($0)") }
        XCTAssertThrowsError(try DecisionQuestion(type: .choice, instructions: "Pick", options: many))
    }

    func testDuplicateIds_areRejected() throws {
        XCTAssertThrowsError(try DecisionQuestion(
            type: .choice,
            instructions: "Pick",
            options: [try DecisionOption(id: "a", text: "A"), try DecisionOption(id: "a", text: "B")]
        ))
    }

    func testBlankInputs_areRejected() throws {
        XCTAssertThrowsError(try DecisionRequest(state: "  ", question: try .noul(instructions: "Q?")))
        XCTAssertThrowsError(try DecisionQuestion.choice(instructions: " ", options: [("a", "A"), ("b", "B")]))
    }

    func testUnknownWireType_isRejected() {
        XCTAssertThrowsError(try QuestionType(wire: "classification"))
    }

    func testTimings_totalAddsStages() {
        var timings = DecisionTimings()
        timings.prepareMs = 1
        timings.lookupMs = 2
        timings.compileMs = 3
        timings.writeMs = 4
        timings.enqueueMs = 5
        timings.readbackMs = 6
        timings.decodeMs = 7
        XCTAssertEqual(timings.graphMs, 15)
        XCTAssertEqual(timings.totalMs, 28)
    }
}
