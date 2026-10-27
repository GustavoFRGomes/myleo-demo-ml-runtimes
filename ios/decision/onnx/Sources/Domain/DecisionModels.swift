import Foundation

/// The three question shapes Julia-1 was trained on. `qtype` is the graph's channel:
/// choice = 0, score = 1, noul (yes/no) = 2.
enum QuestionType: String, CaseIterable, Sendable {
    case choice
    case score
    case noul

    var qtype: Int {
        switch self {
        case .choice: 0
        case .score: 1
        case .noul: 2
        }
    }

    /// Parses the author's `type` string, rejecting anything else.
    init(wire: String) throws {
        guard let value = QuestionType(rawValue: wire) else {
            throw DecisionError.invalidQuestion("Unknown question type: \(wire)")
        }
        self = value
    }
}

enum DecisionError: Error, Equatable, LocalizedError {
    case invalidQuestion(String)
    case encoding(String)
    case model(String)
    case graph(String)

    var errorDescription: String? {
        switch self {
        case .invalidQuestion(let message): message
        case .encoding(let message): "request does not fit: \(message)"
        case .model(let message): "model error: \(message)"
        case .graph(let message): "graph error: \(message)"
        }
    }
}

/// One scorable option: `id` is the caller's label, `text` is the description scored at a marker.
struct DecisionOption: Equatable, Sendable {
    let id: String
    let text: String

    init(id: String, text: String) throws {
        guard !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw DecisionError.invalidQuestion("option ids must not be blank")
        }
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw DecisionError.invalidQuestion("option descriptions must not be blank")
        }
        self.id = id
        self.text = text
    }
}

/// One named question: what it asks and the 2–20 options to score.
/// `noul` is the model's boolean channel and must be ordered `false`, `true` — the decoder
/// reads `p[1]` as P(true).
struct DecisionQuestion: Equatable, Sendable {
    static let falseId = "false"
    static let trueId = "true"

    let type: QuestionType
    let instructions: String
    let options: [DecisionOption]

    init(type: QuestionType, instructions: String, options: [DecisionOption]) throws {
        guard !instructions.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw DecisionError.invalidQuestion("question instructions must not be blank")
        }
        guard (2...20).contains(options.count) else {
            throw DecisionError.invalidQuestion("options must contain 2-20 entries")
        }
        guard Set(options.map(\.id)).count == options.count else {
            throw DecisionError.invalidQuestion("option ids must be unique")
        }
        if type == .noul {
            guard options.count == 2 else {
                throw DecisionError.invalidQuestion("noul needs exactly two options")
            }
            guard options.map(\.id) == [Self.falseId, Self.trueId] else {
                throw DecisionError.invalidQuestion("noul options must be ordered false, true")
            }
        }
        self.type = type
        self.instructions = instructions
        self.options = options
    }

    /// Choice question from `id to description` pairs, preserving order.
    static func choice(instructions: String, options: [(String, String)]) throws -> DecisionQuestion {
        try DecisionQuestion(
            type: .choice,
            instructions: instructions,
            options: options.map { try DecisionOption(id: $0.0, text: $0.1) }
        )
    }

    /// Ordered rubric; ids are the zero-based index strings the decoder returns.
    static func score(instructions: String, rubric: [String]) throws -> DecisionQuestion {
        try DecisionQuestion(
            type: .score,
            instructions: instructions,
            options: rubric.enumerated().map { try DecisionOption(id: String($0.offset), text: $0.element) }
        )
    }

    /// Yes/no question; without descriptions the literal words are the options to score.
    static func noul(
        instructions: String,
        falseText: String = falseId,
        trueText: String = trueId
    ) throws -> DecisionQuestion {
        try DecisionQuestion(
            type: .noul,
            instructions: instructions,
            options: [
                try DecisionOption(id: falseId, text: falseText),
                try DecisionOption(id: trueId, text: trueText),
            ]
        )
    }
}

/// One decision row: the state under consideration plus the question to answer.
struct DecisionRequest: Equatable, Sendable {
    let state: String
    let question: DecisionQuestion

    init(state: String, question: DecisionQuestion) throws {
        guard !state.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw DecisionError.invalidQuestion("state must not be blank")
        }
        self.state = state
        self.question = question
    }
}

/// Compute backend. There is no FP16/NPU option: answers change under fp16 arithmetic.
enum DecisionAccelerator: String, Sendable {
    case gpu = "GPU FP32"
    case cpu = "CPU"
}

/// Per-stage timings for one decision, in milliseconds.
struct DecisionTimings: Equatable, Sendable {
    var prepareMs: Double = 0
    var lookupMs: Double = 0
    var compileMs: Double = 0
    var writeMs: Double = 0
    var enqueueMs: Double = 0
    var readbackMs: Double = 0
    var decodeMs: Double = 0

    var graphMs: Double { writeMs + enqueueMs + readbackMs }
    var totalMs: Double { prepareMs + lookupMs + compileMs + graphMs + decodeMs }
}

/// One answer. `probabilities` follow `question.options`; `choice` is the winning id,
/// `score` the expected zero-based rubric index and `noul` the probability of true.
struct DecisionOutcome: Sendable {
    let question: DecisionQuestion
    let probabilities: [Double]
    let choiceId: String?
    let score: Double?
    let noul: Double?
    let maxProbability: Double?
    let argmax: Int
    let tokenCount: Int
    let window: Int
    let accelerator: DecisionAccelerator
    let timings: DecisionTimings

    var topOption: DecisionOption { question.options[argmax] }
}

/// What actually loaded: which backend, which windows, and how long each phase took.
struct DecisionModelInfo: Sendable {
    let accelerator: DecisionAccelerator
    let defaultWindow: Int
    let availableWindows: [Int]
    let tokenizerLoadMs: Double
    let tableLoadMs: Double
    let graphLoadMs: Double
    let totalLoadMs: Double

    var backend: String { accelerator.rawValue }
}

/// The decision seam: load once, then one forward pass per request. Deliberately not a
/// chat contract — Julia never generates tokens, so there is nothing to stream or cancel.
protocol Decider: AnyObject, Sendable {
    func load() async throws -> DecisionModelInfo
    func decide(_ request: DecisionRequest) async throws -> DecisionOutcome
}
