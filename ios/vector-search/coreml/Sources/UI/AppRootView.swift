import SwiftUI
import UIKit

enum AppScreen: Equatable {
    case search
    case ingest
    case detail(SearchHit)
}

private struct ShareItem: Identifiable {
    let id = UUID()
    let url: URL
    let mimeType: String
}

struct ShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

struct AppRootView: View {
    @StateObject var searchStore: SearchStore
    @StateObject var ingestStore: IngestStore
    let systemStats: SystemStatsProvider
    let appLabel: String

    @State private var screen = AppScreen.search
    @State private var showStats = false
    @State private var shareItem: ShareItem?

    var body: some View {
        Group {
            switch screen {
            case .search:
                SearchScreen(
                    store: searchStore,
                    onOpenIngest: { screen = .ingest },
                    onShowStats: { showStats = true },
                    onOpenHit: { screen = .detail($0) }
                )
            case .ingest:
                IngestScreen(
                    store: ingestStore,
                    onBack: { screen = .search }
                )
            case .detail(let hit):
                ResultDetailScreen(
                    hit: hit,
                    query: searchStore.state.query,
                    onBack: { screen = .search }
                )
            }
        }
        .sheet(isPresented: $showStats) {
            StatsSheetContent(data: reportData(), onExport: export)
        }
        .sheet(item: $shareItem) { item in
            ShareSheet(url: item.url)
        }
    }

    private func reportData() -> StatsReportData {
        let ingest = ingestStore.state
        let search = searchStore.state
        return StatsReportData(
            device: systemStats.snapshot(),
            modelName: ingest.modelName,
            modelLoadMs: ingest.modelLoadMs,
            corpus: ingest.corpus,
            documents: ingest.documents,
            lastRun: ingest.lastRun,
            lastQuery: search.queryMs.map { QueryStats(query: search.query, hits: search.hits.count, ms: $0) }
        )
    }

    private func export(_ format: StatsFormat) {
        let generatedAt = ISO8601DateFormatter().string(from: Date())
        let stamp = statsFileStamp()
        let fileName = "myleo-stats-\(stamp).\(format.fileExtension)"
        let content = format.render(appLabel: appLabel, data: reportData(), generatedAt: generatedAt)
        let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        do {
            let url = try StatsExporter(directory: directory).export(fileName: fileName, content: content)
            shareItem = ShareItem(url: url, mimeType: format.mimeType)
        } catch {
            // Sheet stays open; export errors are silent by design here —
            // production code would surface a toast/alert.
            print("stats export failed: \(error)")
        }
    }
}

private func statsFileStamp() -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyyMMdd-HHmmss"
    return formatter.string(from: Date())
}
