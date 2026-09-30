import SwiftUI

@main
struct MyLeoLLMCoreMLApp: App {
    @StateObject private var controller = RuntimeController()

    var body: some Scene {
        WindowGroup {
            ChatScreen(
                store: controller.store,
                runtime: controller.runtime,
                onSelectRuntime: { controller.select($0) }
            )
            .id(controller.runtime)
        }
    }
}
