import SwiftUI

@main
struct MyLeoLLMLiteRTLMApp: App {
    @StateObject private var store: ChatStore

    init() {
        let downloader = HuggingFaceModelDownloader(
            destinationDirectory: ModelLocator.modelsDirectory()
        )
        _store = StateObject(wrappedValue: ChatStore(
            locate: { ModelLocator.locate() },
            makeInference: { LiteRTLMEngine(modelURL: $0) },
            downloader: downloader
        ))
    }

    var body: some Scene {
        WindowGroup {
            ChatScreen(store: store)
        }
    }
}
