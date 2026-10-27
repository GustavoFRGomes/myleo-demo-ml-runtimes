import XCTest
@testable import MyLeoDecisionLiteRT

private struct FixedEncoder: JuliaEncoding {
    func encode(_ text: String) -> [Int] { text.unicodeScalars.map { Int($0.value % 3) } }
}

private final class FakeGraph: JuliaGraphRunning, @unchecked Sendable {
    let window: Int
    let accelerator: DecisionAccelerator = .cpu
    let logits: (Int) -> Float

    init(window: Int, logits: @escaping (Int) -> Float) {
        self.window = window
        self.logits = logits
    }

    func run(embeddings: [Float], attentionMask: [Float], qtypeOnehot: [Float]) throws -> JuliaGraphRun {
        XCTAssertEqual(embeddings.count, window * JuliaTokenTable.width)
        XCTAssertEqual(attentionMask.count, window)
        let values = (0..<window).map { logits($0) }
        return JuliaGraphRun(tokenLogits: values, writeMs: 0.1, enqueueMs: 0.2, readbackMs: 0.3)
    }
}

final class JuliaEngineTests: XCTestCase {
    private var tableURL: URL!

    /// A tiny vocabulary (4 rows) at the production width, so engine/table geometry agree.
    private func makeTable() throws -> URL {
        let width = JuliaTokenTable.width
        var data = Data(capacity: 8 * width * 2)
        for row in 0..<8 {
            for column in 0..<width {
                var bits = Float16(Float(row) + Float(column) / 1000).bitPattern.littleEndian
                withUnsafeBytes(of: &bits) { data.append(contentsOf: $0) }
            }
        }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("engine-table-\(UUID().uuidString).bin")
        try data.write(to: url)
        return url
    }

    override func tearDownWithError() throws {
        if let tableURL { try? FileManager.default.removeItem(at: tableURL) }
    }

    private func makeEngine(
        window: Int = 128,
        encoder: JuliaEncoding = FixedEncoder(),
        graphs: [Int: JuliaGraphRunning]
    ) throws -> JuliaEngine {
        tableURL = try makeTable()
        let table = try JuliaTokenTable(fileURL: tableURL, vocabularySize: 8, width: JuliaTokenTable.width, padId: 0)
        let report = DecisionModelInfo(
            accelerator: .cpu, defaultWindow: window, availableWindows: graphs.keys.sorted(),
            tokenizerLoadMs: 0, tableLoadMs: 0, graphLoadMs: 0, totalLoadMs: 0
        )
        return JuliaEngine(
            encoder: encoder,
            table: table,
            availableWindows: graphs.keys.sorted(),
            defaultWindow: window,
            loadReport: report,
            graph: { w in
                guard let graph = graphs[w] else { throw DecisionError.model("no graph \(w)") }
                return graph
            }
        )
    }

    func testDecide_choice_returnsWinnerAndTimings() async throws {
        let engine = try makeEngine(graphs: [
            128: FakeGraph(window: 128) { marker in marker == 0 ? 5 : 0 },
        ])
        let question = try DecisionQuestion.choice(
            instructions: "Pick",
            options: [("a", "alpha"), ("b", "beta")]
        )
        let outcome = try await engine.decide(try DecisionRequest(state: "state", question: question))

        XCTAssertEqual(outcome.choiceId, "a")
        XCTAssertEqual(outcome.window, 128)
        XCTAssertEqual(outcome.accelerator, .cpu)
        XCTAssertEqual(outcome.probabilities.count, 2)
        XCTAssertEqual(outcome.timings.graphMs, 0.6, accuracy: 1e-9)
        XCTAssertGreaterThanOrEqual(outcome.timings.totalMs, 0)
        XCTAssertGreaterThan(outcome.tokenCount, 0)
    }

    func testDecide_requestOverSmallWindow_usesLargerGraph() async throws {
        // Window 8 cannot fit a long state; the engine retries with the installed 64 window.
        let engine = try makeEngine(window: 8, graphs: [
            8: FakeGraph(window: 8) { _ in 0 },
            64: FakeGraph(window: 64) { _ in 0 },
        ])

        let question = try DecisionQuestion.noul(instructions: "Is it?")
        let request = try DecisionRequest(state: String(repeating: "x", count: 20), question: question)
        let outcome = try await engine.decide(request)

        XCTAssertEqual(outcome.window, 64)
        XCTAssertEqual(outcome.noul ?? 0, 0.5, accuracy: 1e-12)
    }
}
