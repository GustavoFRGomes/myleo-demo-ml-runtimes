import SwiftUI

@main
struct MyLeoLLMCoreMLApp: App {
    @StateObject private var store: ChatStore

    init() {
        _store = StateObject(wrappedValue: ChatStore(
            locate: { SystemModelLocator.locate() },
            makeInference: { _ in
                if #available(iOS 26.0, *) {
                    return FoundationModelsEngine()
                } else {
                    return UnavailableInference()
                }
            },
            downloader: DisabledModelDownloader(),
            downloadEnabled: false
        ))
    }

    var body: some Scene {
        WindowGroup {
            ChatScreen(store: store)
        }
    }
}
