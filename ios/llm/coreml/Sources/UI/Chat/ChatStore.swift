import Combine
import Foundation
import os

/// Chat screen store (MVI): owns model download + load state (F-LLM-1), turns
/// intents into streaming generations (F-LLM-2), and reports TTFT + tokens/sec
/// (F-LLM-4). Cancel/regenerate/clear cover F-LLM-3.
///
/// Seams (`locate`, `makeInference`, `downloader`) are injected so unit tests
/// never touch the file system, network, or the runtime.
@MainActor
final class ChatStore: ObservableObject {
    @Published private(set) var state = ChatState()

    private let locate: @Sendable () -> URL?
    private let makeInference: @Sendable (URL) -> ChatInference
    private let downloader: ModelDownloading

    private var inference: ChatInference?
    private var generationTask: Task<Void, Never>?
    private var downloadTask: Task<Void, Never>?
    private let log = Logger(subsystem: "org.ggomes.myleo.llm.coreml", category: "Chat")

    init(
        locate: @escaping @Sendable () -> URL?,
        makeInference: @escaping @Sendable (URL) -> ChatInference,
        downloader: ModelDownloading,
        downloadEnabled: Bool = true
    ) {
        self.locate = locate
        self.makeInference = makeInference
        self.downloader = downloader
        state.downloadEnabled = downloadEnabled
    }

    func send(_ intent: ChatIntent) {
        switch intent {
        case .load, .retryLoad:
            loadModel()
        case .downloadModel:
            downloadModel()
        case .cancelDownload:
            cancelDownload()
        case .inputChanged(let text):
            state.input = text
        case .systemPromptChanged(let text):
            state.systemPrompt = text
        case .paramsChanged(let params):
            state.params = params.clamped()
        case .send:
            beginSend()
        case .cancel:
            cancelGeneration()
        case .regenerate:
            beginRegenerate()
        case .clear:
            clearThread()
        case .dismissError:
            state.error = nil
        }
    }

    // MARK: - Model lifecycle

    private func loadModel() {
        guard !state.status.isLoading else { return }
        guard let url = locate() else {
            state.status = .failed(ChatError.modelNotFound(SystemModelLocator.hint).localizedDescription)
            return
        }
        let inference = self.inference ?? makeInference(url)
        self.inference = inference
        state.status = .loading
        state.error = nil
        Task { [weak self] in
            guard let self else { return }
            do {
                let info = try await inference.loadModel()
                self.state.status = .ready(info)
                self.state.download = .finished
                self.log.debug("model loaded: \(info.displayName) in \(info.loadMs)ms")
            } catch {
                self.state.status = .failed(error.localizedDescription)
            }
        }
    }

    private func downloadModel() {
        guard !state.download.isDownloading else { return }
        state.download = .downloading(ModelDownloadProgress(receivedBytes: 0, totalBytes: 0))
        state.error = nil
        downloadTask = Task { [weak self] in
            guard let self else { return }
            do {
                _ = try await self.downloader.downloadModel { progress in
                    Task { @MainActor [weak self] in
                        guard let self, self.state.download.isDownloading else { return }
                        self.state.download = .downloading(progress)
                    }
                }
                self.state.download = .finished
                self.state.status = .idle
                self.loadModel()
            } catch is CancellationError {
                self.state.download = .idle
            } catch {
                self.state.download = .failed(error.localizedDescription)
            }
        }
    }

    private func cancelDownload() {
        downloadTask?.cancel()
        Task { [downloader] in await downloader.cancelDownload() }
        state.download = .idle
    }

    // MARK: - Generation

    private func beginSend() {
        let text = state.input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !state.isGenerating, state.status.info != nil else { return }
        state.messages.append(ChatMessage(role: .user, text: text))
        state.input = ""
        state.messages.append(ChatMessage(role: .assistant, text: "", isStreaming: true))
        startGeneration()
    }

    private func beginRegenerate() {
        guard !state.isGenerating, state.status.info != nil else { return }
        while let last = state.messages.last, last.role == .assistant {
            state.messages.removeLast()
        }
        guard state.messages.last?.role == .user else { return }
        state.messages.append(ChatMessage(role: .assistant, text: "", isStreaming: true))
        startGeneration()
    }

    private func clearThread() {
        guard !state.isGenerating else { return }
        state.messages = []
        state.lastMetrics = nil
        state.error = nil
    }

    private func startGeneration() {
        guard let inference else { return }
        let history = state.messages.filter { !$0.isStreaming }
        let systemPrompt = state.systemPrompt
        let params = state.params

        state.isGenerating = true
        state.lastMetrics = nil
        state.error = nil

        generationTask = Task { [weak self] in
            guard let self else { return }
            let startedAt = Date()
            let tokensBefore = await inference.tokenCount()
            do {
                let stream = try await inference.generate(
                    systemPrompt: systemPrompt,
                    messages: history,
                    params: params
                )
                var firstChunkAt: Date?
                for try await chunk in stream {
                    if Task.isCancelled { break }
                    if firstChunkAt == nil, !chunk.isEmpty { firstChunkAt = Date() }
                    self.appendChunk(chunk)
                }
                self.finishGeneration(
                    startedAt: startedAt,
                    firstChunkAt: firstChunkAt,
                    tokensBefore: tokensBefore,
                    tokensAfter: await inference.tokenCount(),
                    cancelled: Task.isCancelled
                )
            } catch {
                if Task.isCancelled || error is CancellationError {
                    self.finishGeneration(
                        startedAt: startedAt,
                        firstChunkAt: nil,
                        tokensBefore: tokensBefore,
                        tokensAfter: await inference.tokenCount(),
                        cancelled: true
                    )
                } else {
                    self.failGeneration(error)
                }
            }
        }
    }

    private func cancelGeneration() {
        generationTask?.cancel()
        Task { [inference] in await inference?.cancel() }
        endStreamingMessages()
        state.isGenerating = false
    }

    private func appendChunk(_ chunk: String) {
        guard let index = state.messages.lastIndex(where: { $0.isStreaming }) else { return }
        state.messages[index].text += chunk
    }

    private func finishGeneration(
        startedAt: Date,
        firstChunkAt: Date?,
        tokensBefore: Int,
        tokensAfter: Int,
        cancelled: Bool
    ) {
        endStreamingMessages()
        state.isGenerating = false
        let endedAt = Date()
        let totalMs = Int64(endedAt.timeIntervalSince(startedAt) * 1000)
        let ttftMs = firstChunkAt.map { Int64($0.timeIntervalSince(startedAt) * 1000) } ?? totalMs
        let metrics = GenerationMetrics(
            ttftMs: ttftMs,
            totalMs: totalMs,
            generatedTokens: max(tokensAfter - tokensBefore, 0)
        )
        state.lastMetrics = metrics
        // Attach the timing to the assistant turn it describes (per-message).
        if let index = state.messages.lastIndex(where: { $0.role == .assistant }) {
            state.messages[index].metrics = metrics
        }
        log.debug("generation \(cancelled ? "cancelled" : "finished"): ttft \(ttftMs)ms, total \(totalMs)ms")
    }

    private func failGeneration(_ error: Error) {
        endStreamingMessages()
        state.isGenerating = false
        state.error = error.localizedDescription
    }

    private func endStreamingMessages() {
        for index in state.messages.indices where state.messages[index].isStreaming {
            state.messages[index].isStreaming = false
        }
    }
}
