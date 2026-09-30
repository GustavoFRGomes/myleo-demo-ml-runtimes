import SwiftUI

@main
struct MyLeoLLMTrioApp: App {
    @StateObject private var session = RuntimeSession()

    var body: some Scene {
        WindowGroup {
            AppRootView(session: session)
        }
    }
}
