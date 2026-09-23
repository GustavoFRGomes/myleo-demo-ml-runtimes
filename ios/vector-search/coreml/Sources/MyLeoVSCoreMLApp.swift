import SwiftUI

@main
struct MyLeoVSCoreMLApp: App {
    @StateObject private var searchStore: SearchStore
    @StateObject private var ingestStore: IngestStore
    private let systemStats: IOSSystemStatsProvider

    init() {
        // No Core ML embedder yet: the missing-model placeholder keeps the app
        // runnable and its error state demoable until one lands.
        let stats = IOSSystemStatsProvider()
        let repository = DefaultEmbeddingRepository(embedder: MissingModelEmbedder())
        let useCase = CalculateEmbeddingUseCase(repository: repository)
        let documents = InMemoryDocumentRepository()
        _searchStore = StateObject(wrappedValue: SearchStore(useCase: useCase, documents: documents))
        _ingestStore = StateObject(wrappedValue: IngestStore(
            useCase: useCase,
            documents: documents,
            systemStats: stats
        ))
        self.systemStats = stats
    }

    var body: some Scene {
        WindowGroup {
            AppRootView(
                searchStore: searchStore,
                ingestStore: ingestStore,
                systemStats: systemStats,
                appLabel: "MyLeo VS Core ML"
            )
        }
    }
}
