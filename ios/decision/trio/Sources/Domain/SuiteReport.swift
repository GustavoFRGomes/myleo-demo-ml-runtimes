import Foundation

struct TypeAggregate: Sendable {
    let type: QuestionType
    let count: Int
    let promptMs: Double
    let minMs: Double
    let maxMs: Double
    var averageMs: Double { count == 0 ? 0 : promptMs / Double(count) }
}

struct SuiteReport: Sendable {
    let runtimeLabel: String
    let backend: String
    let window: Int
    let device: String
    let osVersion: String
    let loadMs: Double
    let tokenizerLoadMs: Double
    let graphLoadMs: Double
    let batteryStartPct: Double
    let batteryEndPct: Double
    let batteryState: String
    let thermal: String
    let startedAt: Date
    let finishedAt: Date
    let results: [SuiteResult]

    var promptMs: Double { results.reduce(0) { $0 + $1.outcome.timings.totalMs } }
    var wallMs: Double { finishedAt.timeIntervalSince(startedAt) * 1000 }
    var averageMs: Double { results.isEmpty ? 0 : promptMs / Double(results.count) }
    var minMs: Double { results.map(\.outcome.timings.totalMs).min() ?? 0 }
    var maxMs: Double { results.map(\.outcome.timings.totalMs).max() ?? 0 }
    var batteryDeltaPct: Double { max(0, batteryStartPct - batteryEndPct) }

    func aggregate(_ type: QuestionType) -> TypeAggregate {
        let rows = results.filter { $0.item.group == type }
        let values = rows.map(\.outcome.timings.totalMs)
        return TypeAggregate(
            type: type,
            count: rows.count,
            promptMs: values.reduce(0, +),
            minMs: values.min() ?? 0,
            maxMs: values.max() ?? 0
        )
    }

    var aggregates: [TypeAggregate] { QuestionType.allCases.map(aggregate) }

    var fileStem: String {
        "julia-1-suite-\(runtimeLabel.lowercased().replacingOccurrences(of: " ", with: "-"))"
    }

    func markdown() -> String {
        var out = """
        # Julia-1 suite — \(runtimeLabel)

        - Backend: \(backend)
        - Window: \(window) tokens
        - Device: \(device) · iOS \(osVersion)
        - Load: \(Self.ms(loadMs)) total (tokenizer \(Self.ms(tokenizerLoadMs)), graph \(Self.ms(graphLoadMs)))
        - Prompts: \(results.count) · \(Self.ms(promptMs)) prompt time · \(Self.ms(averageMs)) average · \(Self.ms(minMs))–\(Self.ms(maxMs))
        - Wall clock: \(Self.ms(wallMs))
        - Battery: \(Int(batteryStartPct))% → \(Int(batteryEndPct))% (\(batteryState)) · thermal \(thermal)

        ## Performance by question type

        | Type | Count | Total | Average | Min | Max |
        |---|---:|---:|---:|---:|---:|
        """
        for aggregate in aggregates {
            out += "\n| \(aggregate.type.rawValue) | \(aggregate.count) | \(Self.ms(aggregate.promptMs)) | \(Self.ms(aggregate.averageMs)) | \(Self.ms(aggregate.minMs)) | \(Self.ms(aggregate.maxMs)) |"
        }
        out += "\n\n## Prompts\n\n| ID | Type | Tokens | Window | Total | Answer |\n|---|---|---:|---:|---:|---|"
        for row in results {
            out += "\n| \(row.item.id) | \(row.item.group.rawValue) | \(row.outcome.tokenCount) | \(row.outcome.window) | \(Self.ms(row.outcome.timings.totalMs)) | \(Self.answer(row.outcome)) |"
        }
        return out + "\n"
    }

    func json() -> String {
        var prompts: [String] = []
        for row in results {
            let answer = Self.answer(row.outcome).replacingOccurrences(of: "\"", with: "\\\"")
            prompts.append("""
                {"id": "\(row.item.id)", "type": "\(row.item.group.rawValue)", "tokens": \(row.outcome.tokenCount), "window": \(row.outcome.window), "totalMs": \(Self.num(row.outcome.timings.totalMs)), "answer": "\(answer)"}
            """)
        }
        var types: [String] = []
        for aggregate in aggregates {
            types.append("""
                {"type": "\(aggregate.type.rawValue)", "count": \(aggregate.count), "promptMs": \(Self.num(aggregate.promptMs)), "averageMs": \(Self.num(aggregate.averageMs)), "minMs": \(Self.num(aggregate.minMs)), "maxMs": \(Self.num(aggregate.maxMs))}
            """)
        }
        return """
        {
          "schema": "myleo.julia1.suite/1",
          "runtime": "\(runtimeLabel)",
          "backend": "\(backend)",
          "window": \(window),
          "device": {"model": "\(device)", "osVersion": "\(osVersion)"},
          "loadMs": \(Self.num(loadMs)),
          "tokenizerLoadMs": \(Self.num(tokenizerLoadMs)),
          "graphLoadMs": \(Self.num(graphLoadMs)),
          "performance": {
            "promptMs": \(Self.num(promptMs)),
            "wallMs": \(Self.num(wallMs)),
            "averageMs": \(Self.num(averageMs)),
            "minMs": \(Self.num(minMs)),
            "maxMs": \(Self.num(maxMs)),
            "byType": [\(types.joined(separator: ","))]
          },
          "battery": {"startPct": \(Self.num(batteryStartPct)), "endPct": \(Self.num(batteryEndPct)), "state": "\(batteryState)", "thermal": "\(thermal)"},
          "prompts": [\(prompts.joined(separator: ","))]
        }

        """
    }

    private static func answer(_ outcome: DecisionOutcome) -> String {
        if let choice = outcome.choiceId { return "→ \(choice)" }
        if let score = outcome.score { return String(format: "→ score %.3f", score) }
        if let noul = outcome.noul { return String(format: "→ P(true) %.3f", noul) }
        return "→ —"
    }

    private static func ms(_ value: Double) -> String { String(format: "%.1f ms", value) }
    private static func num(_ value: Double) -> String { String(format: "%.2f", value) }
}
