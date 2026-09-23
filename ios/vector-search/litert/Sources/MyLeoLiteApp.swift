import SwiftUI

@main
struct MyLeoLiteApp: App {
    @StateObject private var searchStore: SearchStore
    @StateObject private var ingestStore: IngestStore
    private let systemStats: IOSSystemStatsProvider

    init() {
        let stats = IOSSystemStatsProvider()
        let start = Date()
        let embedder: Embedder
        var loadMs: Int64?
        var modelName: String?
        do {
            embedder = try LiteRTEmbedderFactory.create()
            loadMs = Int64(Date().timeIntervalSince(start) * 1000)
            modelName = LiteRTEmbedderFactory.defaultModelFileName
        } catch {
            embedder = MissingModelEmbedder()
        }
        let repository = DefaultEmbeddingRepository(embedder: embedder)
        let useCase = CalculateEmbeddingUseCase(repository: repository)
        let documents = InMemoryDocumentRepository()
        let dataset = BundleEnronDatasetLoader()
        _searchStore = StateObject(wrappedValue: SearchStore(useCase: useCase, documents: documents))
        _ingestStore = StateObject(wrappedValue: IngestStore(
            useCase: useCase,
            documents: documents,
            modelLoadMs: loadMs,
            modelName: modelName,
            systemStats: stats,
            datasetLoader: dataset
        ))
        self.systemStats = stats
    }

    var body: some Scene {
        WindowGroup {
            AppRootView(
                searchStore: searchStore,
                ingestStore: ingestStore,
                systemStats: systemStats,
                appLabel: "MyLeo LiteRT"
            )
        }
    }
}
