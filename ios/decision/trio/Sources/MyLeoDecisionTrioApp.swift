import SwiftUI

@main
struct MyLeoDecisionTrioApp: App {
    var body: some Scene {
        WindowGroup {
            if SuiteBench.requested {
                ProgressView("Running 18-prompt suite…")
                    .task { await SuiteBench.run() }
            } else if TrioBench.requested {
                ProgressView("Running trio decision benchmark…")
                    .task { await TrioBench.run() }
            } else {
                DecisionScreen()
            }
        }
    }
}
