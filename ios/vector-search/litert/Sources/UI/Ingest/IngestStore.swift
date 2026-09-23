import Combine
import Foundation
import os

/// Ingest screen store (MVI): chunks the corpus (F-VS-1), embeds each chunk
/// (F-VS-2), persists them (F-VS-3) and reports timings, battery + index
/// totals. One ingest run = one document ("Document N").
@MainActor
final class IngestStore: ObservableObject {
    @Published private(set) var state: IngestState

    private let useCase: CalculateEmbeddingUseCase
    private let documents: DocumentRepository
    private let systemStats: SystemStatsProvider
    private let datasetLoader: EmailDatasetLoader?
    private let log = Logger(subsystem: "org.ggomes.myleolite", category: "Ingest")

    init(
        useCase: CalculateEmbeddingUseCase,
        documents: DocumentRepository,
        modelLoadMs: Int64? = nil,
        modelName: String? = nil,
        modelUrl: String? = nil,
        modelDimensions: Int? = nil,
        chunkSize: Int? = nil,
        systemStats: SystemStatsProvider = UnknownSystemStats(),
        datasetLoader: EmailDatasetLoader? = nil
    ) {
        self.useCase = useCase
        self.documents = documents
        self.systemStats = systemStats
        self.datasetLoader = datasetLoader
        self.state = IngestState(modelName: modelName, modelUrl: modelUrl, modelLoadMs: modelLoadMs, modelDimensions: modelDimensions, chunkSize: chunkSize)
        Task.detached { [weak self] in
            await self?.refreshIndexState()
        }
    }

    func send(_ intent: IngestIntent) {
        switch intent {
        case .corpusChanged(let text):
            state.corpusText = text
            state.error = nil
        case .sourceChanged(let source):
            state.source = source
            state.error = nil
        case .itemCountChanged(let count):
            state.itemCount = max(1, count)
            state.error = nil
        case .ingest:
            guard !state.isIngesting else { return }
            switch state.source {
            case .freeText:
                ingestFreeText()
            case .enronSample:
                ingestEnronSample()
            }
        case .clearIndex:
            guard !state.isIngesting else { return }
            Task.detached { [weak self] in
                await self?.runClear()
            }
        }
    }

    private struct IngestGroup {
        let isEmail: Bool
        let source: String
        let chunks: [String]
    }

    private func ingestFreeText() {
        let corpus = state.corpusText
        let groups = [IngestGroup(isEmail: false, source: corpus, chunks: chunkCorpus(corpus))]
        guard groups.contains(where: { !$0.chunks.isEmpty }) else { return }
        beginRun()
        Task.detached { [weak self] in
            await self?.runIngest(groups: groups)
        }
    }

    private func ingestEnronSample() {
        guard let datasetLoader else {
            state.error = "no dataset loader wired"
            return
        }
        let count = state.itemCount
        beginRun()
        Task.detached { [weak self] in
            guard let self else { return }
            let emails: [String]
            do {
                emails = try datasetLoader.loadEmails(count: count)
            } catch {
                await MainActor.run {
                    self.state.isIngesting = false
                    self.state.error = error.localizedDescription
                }
                return
            }
            // Each email raw string goes through the same text chunker and
            // becomes its own document, keeping the parent email for detail.
            let groups = emails.map { email in
                IngestGroup(isEmail: true, source: email, chunks: chunkCorpus(email))
            }.filter { !$0.chunks.isEmpty }
            await self.runIngest(groups: groups)
        }
    }

    private func beginRun() {
        state.isIngesting = true
        state.error = nil
        state.chunks = []
        state.stored = false
        state.lastRun = nil
    }

    private func runClear() async {
        do {
            try documents.clear()
            await MainActor.run {
                state.chunks = []
                state.stored = false
                state.lastRun = nil
                state.corpus = CorpusStats()
                state.documents = []
            }
            log.debug("index cleared")
        } catch {
            await MainActor.run { state.error = error.localizedDescription }
        }
    }

    private func runIngest(groups: [IngestGroup]) async {
        var number = (try? documents.stats())?.documents ?? 0
        let batteryStart = systemStats.snapshot().batteryPercent
        var embedded: [(label: String, text: String, vector: [Float], ms: Int64, source: String)] = []
        var embedTotalMs: Int64 = 0
        var storeMs: Int64 = 0
        var stored = false
        var firstError: String?
        var labels: [String] = []
        let start = Date()
        var counter = 0
        let total = groups.reduce(0) { $0 + $1.chunks.count }
        for group in groups {
            number += 1
            let label = group.isEmail ? "Email \(number)" : "Document \(number)"
            labels.append(label)
            var groupEmbedded: [(text: String, vector: [Float], ms: Int64)] = []
            for chunk in group.chunks {
                do {
                    let chunkStart = Date()
                    let vector = try useCase(chunk)
                    let ms = ms(since: chunkStart)
                    groupEmbedded.append((chunk, vector, ms))
                    embedTotalMs += ms
                    counter += 1
                    log.debug("embed chunk \(counter)/\(total) (\(chunk.count) chars) in \(ms)ms")
                } catch {
                    firstError = firstError ?? error.localizedDescription
                }
            }
            if !groupEmbedded.isEmpty, firstError == nil {
                let storeStart = Date()
                do {
                    try documents.ingest(
                        document: label,
                        chunks: groupEmbedded.map {
                            ChunkEmbedding(text: $0.text, vector: $0.vector, source: group.source)
                        }
                    )
                    stored = true
                } catch {
                    firstError = error.localizedDescription
                }
                storeMs += ms(since: storeStart)
            }
            embedded += groupEmbedded.map { (label, $0.text, $0.vector, $0.ms, group.source) }
        }
        let totalMs = ms(since: start)
        let batteryEnd = systemStats.snapshot().batteryPercent
        let snapshot = embedded.map { IngestedChunk(text: $0.text, dimensions: $0.vector.count, embedMs: $0.ms) }
        let corpus = (try? documents.stats()) ?? CorpusStats()
        let perDocument = (try? documents.documentStats()) ?? []
        let runLabel: String
        switch labels.count {
        case 0:
            runLabel = "no documents"
        case 1:
            runLabel = labels[0]
        default:
            runLabel = "Emails \(number - labels.count + 1)-\(number)"
        }
        let run = IngestRunStats(
            document: runLabel, chunks: snapshot.count,
            embedMs: embedTotalMs, storeMs: storeMs, totalMs: totalMs,
            batteryStartPct: batteryStart, batteryEndPct: batteryEnd
        )
        await MainActor.run {
            state.isIngesting = false
            state.chunks = snapshot
            state.stored = stored
            state.lastRun = run
            state.corpus = corpus
            state.documents = perDocument
            state.error = firstError
        }
        log.debug("ingested \(runLabel): \(snapshot.count) chunks in \(totalMs)ms (embed \(embedTotalMs)ms, store \(storeMs)ms), battery \(Self.pct(batteryStart)) → \(Self.pct(batteryEnd))")
        log.debug("index holds \(corpus.documents) documents, \(corpus.chunks) chunks")
    }

    private func refreshIndexState() async {
        let corpus: CorpusStats
        do {
            corpus = try documents.stats()
        } catch {
            log.warning("stats failed: \(error.localizedDescription)")
            corpus = CorpusStats()
        }
        let perDocument = (try? documents.documentStats()) ?? []
        let available = try? datasetLoader?.availableEmails()
        await MainActor.run {
            state.corpus = corpus
            state.documents = perDocument
            if let available {
                state.availableItems = available
                state.itemCount = min(max(state.itemCount, 1), available)
            }
        }
    }

    private static func pct(_ value: Int?) -> String {
        value.map { "\($0)%" } ?? "n/a"
    }
}

private func ms(since date: Date) -> Int64 {
    Int64(Date().timeIntervalSince(date) * 1000)
}
