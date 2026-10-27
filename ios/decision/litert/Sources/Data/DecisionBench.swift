import Foundation

/// End-to-end decision check used from the command line on device:
///
/// ```
/// xcrun devicectl device process launch --console <udid> org.ggomes.myleo.decision.litert \
///   --bench-decision
/// ```
///
/// Builds two engines over the same package — Metal (precision loss disabled, i.e. FP32) and
/// CPU — runs the identical request on each, and prints the probabilities so the two
/// accelerators can be compared answer-for-answer. This is the "A → C" check for the LiteRT
/// leaf: keep Metal only if it reproduces the CPU arithmetic.
enum DecisionBench {
    static var requested: Bool {
        ProcessInfo.processInfo.arguments.contains("--bench-decision")
    }

    static func run() async {
        guard let files = JuliaModelLocator.locate() else {
            print("DECISION|bench|missing-package|\(JuliaModelLocator.directory().path)")
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

            let useCoreML = ProcessInfo.processInfo.arguments.contains("--coreml")
            var outcomes: [DecisionAccelerator: DecisionOutcome] = [:]
            for accelerator in [useCoreML ? DecisionAccelerator.coreML : DecisionAccelerator.gpu, DecisionAccelerator.cpu] {
                do {
                    let engine = try await JuliaEngine.make(files: files, preferGPU: accelerator == .gpu, allowPrecisionLoss: ProcessInfo.processInfo.arguments.contains("--allow-precision-loss"),
                                       useLegacy: !ProcessInfo.processInfo.arguments.contains("--litert23"),
                                       coreML: useCoreML && accelerator == .coreML)
                    let info = try await engine.load()
                    var last: DecisionOutcome?
                    for iteration in 1...3 {
                        let outcome = try await engine.decide(request)
                        last = outcome
                        print(
                            "DECISION|iter|asked=\(accelerator.rawValue)|i=\(iteration)" +
                                "|choice=\(outcome.choiceId ?? "-")" +
                                String(format: "|prepare=%.2f|lookup=%.2f|graph=%.2f|decode=%.2f|total=%.2f",
                                       outcome.timings.prepareMs, outcome.timings.lookupMs,
                                       outcome.timings.graphMs, outcome.timings.decodeMs,
                                       outcome.timings.totalMs)
                        )
                    }
                    guard let outcome = last else { continue }
                    outcomes[accelerator] = outcome
                    print(
                        "DECISION|outcome|asked=\(accelerator.rawValue)|ran=\(outcome.accelerator.rawValue)" +
                            "|window=\(outcome.window)|tokens=\(outcome.tokenCount)" +
                            "|choice=\(outcome.choiceId ?? "-")|argmax=\(outcome.argmax)" +
                            "|probs=\(outcome.probabilities.map { String(format: "%.6f", $0) }.joined(separator: ","))" +
                            String(format: "|prepare=%.2f|lookup=%.2f|graph=%.2f|decode=%.2f|total=%.2f",
                                   outcome.timings.prepareMs, outcome.timings.lookupMs,
                                   outcome.timings.graphMs, outcome.timings.decodeMs,
                                   outcome.timings.totalMs) + String(format: "|load=%.2f", info.totalLoadMs)
                    )
                } catch {
                    print("DECISION|outcome|asked=\(accelerator.rawValue)|error=\(error.localizedDescription)")
                }
            }

            if let gpu = outcomes[.gpu], let cpu = outcomes[.cpu] {
                let diff = zip(gpu.probabilities, cpu.probabilities).map { abs($0 - $1) }.max() ?? 0
                print(
                    "DECISION|compare|gpu=\(gpu.accelerator.rawValue)|cpu=\(cpu.accelerator.rawValue)" +
                        "|sameChoice=\(gpu.choiceId == cpu.choiceId)|maxProbDiff=\(String(format: "%.6f", diff))"
                )
            }
        } catch {
            print("DECISION|bench|error|\(error.localizedDescription)")
            exit(1)
        }
        exit(0)
    }
}
