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
        systemStats: SystemStatsProvider = UnknownSystemStats(),
        datasetLoader: EmailDatasetLoader? = nil
    ) {
        self.useCase = useCase
        self.documents = documents
        self.systemStats = systemStats
        self.datasetLoader = datasetLoader
        self.state = IngestState(modelName: modelName, modelLoadMs: modelLoadMs)
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

    private func ingestFreeText() {
        let chunks = chunkCorpus(state.corpusText)
        guard !chunks.isEmpty else { return }
        beginRun()
        let texts = chunks
        Task.detached { [weak self] in
            await self?.runIngest(texts: texts)
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
            // Each email raw string goes through the same text chunker.
            await self.runIngest(texts: emails.flatMap(chunkCorpus))
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

    private func runIngest(texts: [String]) async {
        let existing = (try? documents.stats()) ?? CorpusStats()
        let label = "Document \(existing.documents + 1)"
        let batteryStart = systemStats.snapshot().batteryPercent
        var embedded: [(text: String, vector: [Float], ms: Int64)] = []
        var embedTotalMs: Int64 = 0
        var storeMs: Int64 = 0
        var stored = false
        var firstError: String?
        let start = Date()
        for (index, chunk) in texts.enumerated() {
            do {
                let chunkStart = Date()
                let vector = try useCase(chunk)
                let ms = ms(since: chunkStart)
                embedded.append((chunk, vector, ms))
                embedTotalMs += ms
                log.debug("embed chunk \(index + 1)/\(texts.count) (\(chunk.count) chars) in \(ms)ms")
            } catch {
                firstError = firstError ?? error.localizedDescription
            }
        }
        if !embedded.isEmpty, firstError == nil {
            let storeStart = Date()
            do {
                try documents.ingest(
                    document: label,
                    chunks: embedded.map { ChunkEmbedding(text: $0.text, vector: $0.vector) }
                )
                stored = true
            } catch {
                firstError = error.localizedDescription
            }
            storeMs = ms(since: storeStart)
        }
        let totalMs = ms(since: start)
        let batteryEnd = systemStats.snapshot().batteryPercent
        let snapshot = embedded.map { IngestedChunk(text: $0.text, dimensions: $0.vector.count, embedMs: $0.ms) }
        let corpus = (try? documents.stats()) ?? CorpusStats()
        let perDocument = (try? documents.documentStats()) ?? []
        let run = IngestRunStats(
            document: label, chunks: snapshot.count,
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
        log.debug("ingested \(label): \(snapshot.count) chunks in \(totalMs)ms (embed \(embedTotalMs)ms, store \(storeMs)ms), battery \(Self.pct(batteryStart)) → \(Self.pct(batteryEnd))")
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
