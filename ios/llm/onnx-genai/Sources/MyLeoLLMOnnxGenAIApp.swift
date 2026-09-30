import SwiftUI

@main
struct MyLeoLLMOnnxGenAIApp: App {
    @StateObject private var store: ChatStore

    init() {
        _store = StateObject(wrappedValue: ChatStore(
            locate: { OnnxGenAIModelLocator.locate() },
            makeInference: { url in
                OnnxGenAIEngine(
                    packageDirectory: url,
                    sizeBytes: OnnxGenAIModelLocator.packageSize()
                )
            },
            downloader: OnnxGenAIFileSetDownloader()
        ))
    }

    var body: some Scene {
        WindowGroup {
            ChatScreen(store: store)
        }
    }
}
