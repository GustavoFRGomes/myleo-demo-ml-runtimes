import SwiftUI

@main
struct MyLeoDecisionOnnxApp: App {
    var body: some Scene {
        WindowGroup {
            if OnnxDecisionBench.requested {
                ProgressView("Running ONNX decision benchmark…")
                    .task { await OnnxDecisionBench.run() }
            } else {
                DecisionPlaceholderView(runtime: "ONNX Runtime", model: "Julia-1 · CPU")
            }
        }
    }
}
