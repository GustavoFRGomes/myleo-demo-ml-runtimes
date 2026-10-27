import SwiftUI

@main
struct MyLeoDecisionTrioApp: App {
    var body: some Scene {
        WindowGroup {
            if TrioBench.requested {
                ProgressView("Running trio decision benchmark…")
                    .task { await TrioBench.run() }
            } else {
                DecisionScreen()
            }
        }
    }
}
