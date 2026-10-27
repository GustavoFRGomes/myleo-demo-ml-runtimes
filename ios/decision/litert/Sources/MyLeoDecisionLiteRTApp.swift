import SwiftUI

@main
struct MyLeoDecisionLiteRTApp: App {
    var body: some Scene {
        WindowGroup {
            if DecisionBench.requested {
                ProgressView("Running decision benchmark…")
                    .task { await DecisionBench.run() }
            } else {
                DecisionBootstrapView()
            }
        }
    }
}
