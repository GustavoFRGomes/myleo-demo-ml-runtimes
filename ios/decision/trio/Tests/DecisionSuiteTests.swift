import XCTest
@testable import MyLeoDecisionTrio

final class DecisionSuiteTests: XCTestCase {
    func testSuite_hasEightFiveFive_andUniqueIds() {
        XCTAssertEqual(DecisionSuite.cases.count, 18)
        XCTAssertEqual(DecisionSuite.of(.choice).count, 8)
        XCTAssertEqual(DecisionSuite.of(.score).count, 5)
        XCTAssertEqual(DecisionSuite.of(.noul).count, 5)
        XCTAssertEqual(Set(DecisionSuite.cases.map(\.id)).count, 18)
    }

    func testReport_formatsMarkdownAndJSON() throws {
        let item = try XCTUnwrap(DecisionSuite.of(.choice).first)
        let outcome = DecisionOutcome(
            question: item.question,
            probabilities: [0.7, 0.2, 0.1],
            choiceId: "billing",
            score: nil,
            noul: nil,
            maxProbability: 0.7,
            argmax: 0,
            tokenCount: 42,
            window: 512,
            accelerator: .cpu,
            timings: DecisionTimings(prepareMs: 1, lookupMs: 2, writeMs: 3, enqueueMs: 4, readbackMs: 5, decodeMs: 6)
        )
        let report = SuiteReport(
            runtimeLabel: "LiteRT", backend: "CPU", window: 512, device: "iPhone", osVersion: "27.0",
            loadMs: 100, tokenizerLoadMs: 60, graphLoadMs: 40,
            batteryStartPct: 80, batteryEndPct: 80, batteryState: "unplugged", thermal: "nominal",
            startedAt: Date(timeIntervalSince1970: 0), finishedAt: Date(timeIntervalSince1970: 1),
            results: [SuiteResult(item: item, outcome: outcome)]
        )
        let markdown = report.markdown()
        XCTAssertTrue(markdown.contains("# Julia-1 suite — LiteRT"))
        XCTAssertTrue(markdown.contains("| choice | 1 |"))
        XCTAssertTrue(markdown.contains(item.id))

        let json = try JSONSerialization.jsonObject(with: Data(report.json().utf8)) as? [String: Any]
        XCTAssertEqual(json?["schema"] as? String, "myleo.julia1.suite/1")
        XCTAssertEqual(json?["runtime"] as? String, "LiteRT")
        XCTAssertEqual((json?["prompts"] as? [[String: Any]])?.count, 1)
    }

    func testReport_aggregatesByType() throws {
        let choice = try XCTUnwrap(DecisionSuite.of(.choice).first)
        let score = try XCTUnwrap(DecisionSuite.of(.score).first)
        func outcome(_ item: SuiteCase, ms: Double) -> SuiteResult {
            SuiteResult(item: item, outcome: DecisionOutcome(
                question: item.question, probabilities: [0.5, 0.5], choiceId: nil, score: 0.5,
                noul: nil, maxProbability: 0.5, argmax: 0, tokenCount: 10, window: 512,
                accelerator: .cpu, timings: DecisionTimings(prepareMs: ms)))
        }
        let report = SuiteReport(
            runtimeLabel: "ONNX", backend: "CPU", window: 1024, device: "iPhone", osVersion: "27.0",
            loadMs: 1, tokenizerLoadMs: 1, graphLoadMs: 0,
            batteryStartPct: 80, batteryEndPct: 80, batteryState: "unplugged", thermal: "nominal",
            startedAt: Date(), finishedAt: Date(), results: [outcome(choice, ms: 10), outcome(score, ms: 30)]
        )
        XCTAssertEqual(report.aggregate(.choice).averageMs, 10, accuracy: 1e-9)
        XCTAssertEqual(report.aggregate(.score).averageMs, 30, accuracy: 1e-9)
        XCTAssertEqual(report.aggregate(.noul).count, 0)
    }
}
