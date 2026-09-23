import SwiftUI

@main
struct MyLeoVSOnnxApp: App {
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
            embedder = try OnnxEmbedderFactory.create()
            loadMs = Int64(Date().timeIntervalSince(start) * 1000)
            modelName = OnnxEmbedderFactory.defaultModelFileName
        } catch {
            embedder = MissingModelEmbedder(reason: (error as? LocalizedError)?.errorDescription ?? error.localizedDescription)
        }
        let repository = DefaultEmbeddingRepository(embedder: embedder)
        let useCase = CalculateEmbeddingUseCase(repository: repository)
        let documents = InMemoryDocumentRepository()
        _searchStore = StateObject(wrappedValue: SearchStore(useCase: useCase, documents: documents))
        _ingestStore = StateObject(wrappedValue: IngestStore(
            useCase: useCase,
            documents: documents,
            modelLoadMs: loadMs,
            modelName: modelName,
            systemStats: stats,
            datasetLoader: BundleEnronDatasetLoader()
        ))
        self.systemStats = stats
    }

    var body: some Scene {
        WindowGroup {
            AppRootView(
                searchStore: searchStore,
                ingestStore: ingestStore,
                systemStats: systemStats,
                appLabel: "MyLeo VS ONNX"
            )
        }
    }
}
