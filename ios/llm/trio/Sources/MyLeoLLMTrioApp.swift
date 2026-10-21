import SwiftUI

@main
struct MyLeoLLMTrioApp: App {
    @StateObject private var session = RuntimeSession()

    var body: some Scene {
        WindowGroup {
            if let benchmark = BenchmarkMode.requestedID {
                BenchmarkScreen(id: benchmark)
            } else {
                AppRootView(session: session)
            }
        }
    }
}
