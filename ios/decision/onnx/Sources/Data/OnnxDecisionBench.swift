import Foundation

/// Device benchmark for the ONNX lane, mirroring the LiteRT `--bench-decision` harness.
enum OnnxDecisionBench {
    static var requested: Bool {
        ProcessInfo.processInfo.arguments.contains("--bench-decision")
    }

    static func run() async {
        guard let files = OnnxJuliaLocator.locate() else {
            print("DECISION|bench|missing-package|\(OnnxJuliaLocator.directory().path)")
            exit(2)
        }
        do {
            let question = try DecisionQuestion.choice(
                instructions: "Which department should handle this ticket?",
                options: [
                    ("billing", "Billing and invoice questions"),
                    ("tech", "Technical problem with the product"),
                    ("returns", "Returns and refunds"),
                ]
            )
            let request = try DecisionRequest(
                state: "My invoice shows a charge I did not authorise for a subscription I cancelled.",
                question: question
            )

            let engine = try await OnnxJuliaEngine.make(files: files)
            let info = try await engine.load()
            let outcome = try await engine.decide(request)
            print(
                "DECISION|outcome|runtime=onnx|asked=CPU|ran=\(outcome.accelerator.rawValue)" +
                    "|window=\(outcome.window)|tokens=\(outcome.tokenCount)" +
                    "|choice=\(outcome.choiceId ?? "-")|argmax=\(outcome.argmax)" +
                    "|probs=\(outcome.probabilities.map { String(format: "%.6f", $0) }.joined(separator: ","))" +
                    String(format: "|prepare=%.2f|graph=%.2f|decode=%.2f|total=%.2f|load=%.2f",
                           outcome.timings.prepareMs, outcome.timings.graphMs,
                           outcome.timings.decodeMs, outcome.timings.totalMs, info.totalLoadMs)
            )
        } catch {
            print("DECISION|bench|error|\(error.localizedDescription)")
            exit(1)
        }
        exit(0)
    }
}
