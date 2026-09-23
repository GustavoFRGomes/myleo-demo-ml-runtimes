import Foundation

/// One corpus chunk with its computed embedding summary.
struct IngestedChunk: Equatable {
    let text: String
    let dimensions: Int
    let embedMs: Int64
}

/// Timing (+ battery) breakdown of one ingest run, for the performance report.
struct IngestRunStats: Equatable {
    let document: String
    let chunks: Int
    let embedMs: Int64
    let storeMs: Int64
    let totalMs: Int64
    var batteryStartPct: Int?
    var batteryEndPct: Int?
}

/// Corpus source: free text or the bundled Enron sample.
enum IngestSource: String, CaseIterable, Equatable {
    case freeText
    case enronSample
}

/// Screen state (MVI). Rendered by the view, never mutated there.
struct IngestState: Equatable {
    var corpusText: String = ""
    var source: IngestSource = .freeText
    var itemCount: Int = 50
    var availableItems: Int?
    var isIngesting: Bool = false
    var chunks: [IngestedChunk] = []
    var stored: Bool = false
    var lastRun: IngestRunStats?
    var corpus = CorpusStats()
    var documents: [DocumentStat] = []
    var modelName: String?
    var modelLoadMs: Int64?
    var error: String?
}

/// User intents (MVI). The view sends these, the store reduces them.
enum IngestIntent {
    case corpusChanged(String)
    case sourceChanged(IngestSource)
    case itemCountChanged(Int)
    case ingest
    case clearIndex
}
