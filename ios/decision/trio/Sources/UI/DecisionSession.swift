import Foundation

/// Screen state for the trio decision form. One engine per runtime, loaded lazily; switching
/// runtime keeps the edited request so the same input can be compared across runtimes.
@MainActor
final class DecisionSession: ObservableObject {
    @Published var runtime: DecisionRuntime = .litert
    @Published var stateText = "My invoice shows a charge I did not authorise for a subscription I cancelled."
    @Published var questionType: QuestionType = .choice
    @Published var options: [String] = [
        "Billing and invoice questions",
        "Technical problem with the product",
        "Returns and refunds",
    ]
    @Published private(set) var outcome: DecisionOutcome?
    @Published private(set) var info: DecisionModelInfo?
    @Published private(set) var error: String?
    @Published private(set) var busy = false

    private var engines: [DecisionRuntime: Decider] = [:]

    var canRun: Bool {
        !busy && runtime.isAvailable && !stateText.trimmingCharacters(in: .whitespaces).isEmpty
            && options.count >= 2 && options.allSatisfy { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
    }

    func select(_ runtime: DecisionRuntime) {
        guard runtime.isAvailable else { return }
        self.runtime = runtime
        outcome = nil
        error = nil
        info = nil
    }

    func run() async {
        guard canRun else { return }
        busy = true
        defer { busy = false }
        error = nil
        do {
            let engine = try await engine(for: runtime)
            info = try await engine.load()
            let question = try makeQuestion()
            let request = try DecisionRequest(state: stateText, question: question)
            outcome = try await engine.decide(request)
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func engine(for runtime: DecisionRuntime) async throws -> Decider {
        if let cached = engines[runtime] { return cached }
        let engine = try await TrioBench.makeEngine(runtime)
        engines[runtime] = engine
        return engine
    }

    private func makeQuestion() throws -> DecisionQuestion {
        switch questionType {
        case .choice:
            return try DecisionQuestion(
                type: .choice,
                instructions: "Which option best fits?",
                options: options.enumerated().map { try DecisionOption(id: "\($0.offset)", text: $0.element) }
            )
        case .score:
            return try DecisionQuestion.score(instructions: "How severe is this?", rubric: options)
        case .noul:
            guard options.count == 2 else {
                throw DecisionError.invalidQuestion("noul needs exactly two options (false, true)")
            }
            return try DecisionQuestion.noul(instructions: "Does this apply?", falseText: options[0], trueText: options[1])
        }
    }
}
