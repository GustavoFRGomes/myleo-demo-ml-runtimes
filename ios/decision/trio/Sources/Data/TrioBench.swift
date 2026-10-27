import Foundation

/// Trio benchmark: the same request through every available decision runtime, in one process.
///
/// ```
/// xcrun devicectl device process launch --console <udid> \
///   org.ggomes.myleo.decision.trio --bench-decision
/// ```
///
/// Each lane prints one `DECISION|outcome|runtime=…` line and three timed iterations, so the
/// numbers line up with the single-runtime leaves and `docs/benchmarks-decision.md`.
enum TrioBench {
    static var requested: Bool {
        ProcessInfo.processInfo.arguments.contains("--bench-decision")
    }

    static func run() async {
        let question: DecisionQuestion
        let request: DecisionRequest
        do {
            question = try DecisionQuestion.choice(
                instructions: "Which department should handle this ticket?",
                options: [
                    ("billing", "Billing and invoice questions"),
                    ("tech", "Technical problem with the product"),
                    ("returns", "Returns and refunds"),
                ]
            )
            request = try DecisionRequest(
                state: "My invoice shows a charge I did not authorise for a subscription I cancelled.",
                question: question
            )
        } catch {
            print("DECISION|bench|error|\(error.localizedDescription)")
            exit(1)
        }

        for runtime in [DecisionRuntime.litert, .onnx] {
            do {
                let engine = try await makeEngine(runtime)
                let info = try await engine.load()
                var last: DecisionOutcome?
                for iteration in 1...3 {
                    let outcome = try await engine.decide(request)
                    last = outcome
                    print(
                        "DECISION|iter|runtime=\(runtime.rawValue)|i=\(iteration)" +
                            "|choice=\(outcome.choiceId ?? "-")" +
                            String(format: "|prepare=%.2f|lookup=%.2f|graph=%.2f|decode=%.2f|total=%.2f",
                                   outcome.timings.prepareMs, outcome.timings.lookupMs,
                                   outcome.timings.graphMs, outcome.timings.decodeMs,
                                   outcome.timings.totalMs)
                    )
                }
                guard let outcome = last else { continue }
                print(
                    "DECISION|outcome|runtime=\(runtime.rawValue)|ran=\(outcome.accelerator.rawValue)" +
                        "|window=\(outcome.window)|tokens=\(outcome.tokenCount)" +
                        "|choice=\(outcome.choiceId ?? "-")|argmax=\(outcome.argmax)" +
                        "|probs=\(outcome.probabilities.map { String(format: "%.6f", $0) }.joined(separator: ","))" +
                        String(format: "|total=%.2f|load=%.2f",
                               outcome.timings.totalMs, info.totalLoadMs)
                )
            } catch {
                print("DECISION|outcome|runtime=\(runtime.rawValue)|error=\(error.localizedDescription)")
            }
        }
        exit(0)
    }

    static func makeEngine(_ runtime: DecisionRuntime) async throws -> Decider {
        switch runtime {
        case .litert:
            guard let files = JuliaModelLocator.locate() else {
                throw DecisionError.model("LiteRT package missing at \(JuliaModelLocator.directory().path)")
            }
            return try await JuliaEngine.make(files: files, preferGPU: false, useLegacy: true)
        case .onnx:
            guard let files = OnnxJuliaLocator.locate() else {
                throw DecisionError.model("ONNX package missing at \(OnnxJuliaLocator.directory().path)")
            }
            return try await OnnxJuliaEngine.make(files: files)
        case .coreML:
            throw DecisionError.model("no Julia-1 Core ML export")
        }
    }
}
