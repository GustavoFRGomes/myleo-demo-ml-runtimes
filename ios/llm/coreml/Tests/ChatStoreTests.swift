import Combine
import XCTest
@testable import MyLeoLLMCoreML

@MainActor
final class ChatStoreTests: XCTestCase {
    private var bag = Set<AnyCancellable>()

    private func waitUntil(
        _ store: ChatStore,
        timeout: TimeInterval = 3,
        _ predicate: @escaping (ChatState) -> Bool
    ) async {
        if predicate(store.state) { return }
        let exp = expectation(description: "state")
        store.$state.dropFirst().first(where: predicate).sink { _ in exp.fulfill() }.store(in: &bag)
        await fulfillment(of: [exp], timeout: timeout)
    }

    private func makeStore(
        inference: FakeChatInference = FakeChatInference(),
        downloader: FakeModelDownloader = FakeModelDownloader(),
        path: PathBox = PathBox(url: URL(fileURLWithPath: "/tmp/fake.litertlm"))
    ) -> ChatStore {
        ChatStore(
            locate: { path.url },
            makeInference: { _ in inference },
            downloader: downloader
        )
    }

    private func readyStore(
        inference: FakeChatInference = FakeChatInference()
    ) async -> (ChatStore, FakeChatInference) {
        let store = makeStore(inference: inference)
        store.send(.load)
        await waitUntil(store) { $0.status.info != nil }
        XCTAssertNotNil(store.state.status.info)
        return (store, inference)
    }

    // MARK: - Model lifecycle

    func test_load_success_setsReadyWithInfo() async {
        let inference = FakeChatInference()
        let store = makeStore(inference: inference)

        store.send(.load)
        await waitUntil(store) { $0.status.info != nil }

        XCTAssertEqual(store.state.status.info?.displayName, "fake.litertlm")
        XCTAssertEqual(inference.loadCount, 1)
    }

    func test_load_missingModel_setsFailedAndOffersDownload() async {
        let store = makeStore(path: PathBox(url: nil))

        store.send(.load)
        await waitUntil(store) { $0.status.isFailed }

        XCTAssertTrue(store.state.status.isFailed)
        XCTAssertTrue(store.state.canDownload)
    }

    func test_load_failure_setsFailed() async {
        let inference = FakeChatInference()
        inference.loadError = ChatError.loadFailed("nope")
        let store = makeStore(inference: inference)

        store.send(.load)
        await waitUntil(store) { $0.status == .failed("failed to load model: nope") }

        XCTAssertEqual(store.state.status, .failed("failed to load model: nope"))
    }

    // MARK: - Download

    func test_downloadModel_successLoadsModelAndReportsProgress() async {
        let path = PathBox(url: nil)
        let downloader = FakeModelDownloader()
        let store = makeStore(downloader: downloader, path: path)
        store.send(.load)
        await waitUntil(store) { $0.status.isFailed }

        // Simulate the file becoming available once the download "completes".
        path.url = URL(fileURLWithPath: "/tmp/fake.litertlm")
        store.send(.downloadModel)
        await waitUntil(store) { $0.status.info != nil }

        XCTAssertEqual(downloader.downloadCount, 1)
        XCTAssertEqual(store.state.download, .finished)
        XCTAssertEqual(store.state.status.info?.displayName, "fake.litertlm")
    }

    func test_downloadModel_failure_setsDownloadFailed() async {
        let downloader = FakeModelDownloader()
        downloader.downloadError = ChatError.downloadFailed("offline")
        let store = makeStore(downloader: downloader, path: PathBox(url: nil))

        store.send(.downloadModel)
        await waitUntil(store) {
            if case .failed = $0.download { return true }
            return false
        }

        XCTAssertEqual(store.state.download, .failed("download failed: offline"))
    }

    func test_cancelDownload_forwardsToDownloader() async {
        let downloader = FakeModelDownloader()
        let store = makeStore(downloader: downloader, path: PathBox(url: nil))

        store.send(.cancelDownload)

        let deadline = Date().addingTimeInterval(3)
        while downloader.cancelCount == 0 && Date() < deadline {
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertEqual(downloader.cancelCount, 1)
        XCTAssertEqual(store.state.download, .idle)
    }

    // MARK: - Generation

    func test_send_streamsChunksIntoAssistantMessage() async {
        let (store, inference) = await readyStore()
        inference.chunks = ["Hello", " world"]

        store.send(.inputChanged("hi"))
        store.send(.send)
        await waitUntil(store) { !$0.isGenerating && $0.messages.count == 2 && $0.lastMetrics != nil }

        XCTAssertEqual(store.state.messages.map(\.role), [.user, .assistant])
        XCTAssertEqual(store.state.messages.last?.text, "Hello world")
        XCTAssertFalse(store.state.messages.last?.isStreaming ?? true)
        XCTAssertEqual(store.state.input, "")
        XCTAssertEqual(inference.generateCalls.last?.messages.last?.text, "hi")
    }

    func test_send_recordsMetricsFromTokenDelta() async {
        let (store, inference) = await readyStore()
        inference.tokenCounts = [0, 7]

        store.send(.inputChanged("hi"))
        store.send(.send)
        await waitUntil(store) { $0.lastMetrics != nil }

        XCTAssertEqual(store.state.lastMetrics?.generatedTokens, 7)
    }

    func test_send_attachesMetricsToAssistantMessageOnly() async {
        let (store, inference) = await readyStore()
        inference.tokenCounts = [0, 5]

        store.send(.inputChanged("hi"))
        store.send(.send)
        await waitUntil(store) { $0.messages.last?.metrics != nil }

        XCTAssertEqual(store.state.messages.last?.metrics?.generatedTokens, 5)
        XCTAssertNil(store.state.messages.first?.metrics)
    }

    func test_send_blankInput_doesNothing() async {
        let (store, inference) = await readyStore()

        store.send(.inputChanged("   "))
        store.send(.send)

        XCTAssertTrue(inference.generateCalls.isEmpty)
        XCTAssertTrue(store.state.messages.isEmpty)
    }

    func test_send_generationError_setsErrorAndClearsStreaming() async {
        let (store, inference) = await readyStore()
        inference.generationError = ChatError.generationFailed("boom")

        store.send(.inputChanged("hi"))
        store.send(.send)
        await waitUntil(store) { $0.error != nil }

        XCTAssertEqual(store.state.error, "generation failed: boom")
        XCTAssertFalse(store.state.isGenerating)
        XCTAssertFalse(store.state.messages.contains { $0.isStreaming })
    }

    func test_regenerate_replacesLastAssistantMessage() async {
        let (store, inference) = await readyStore()
        inference.chunks = ["first"]
        store.send(.inputChanged("hi"))
        store.send(.send)
        await waitUntil(store) { !$0.isGenerating && $0.messages.count == 2 }

        inference.chunks = ["second"]
        store.send(.regenerate)
        await waitUntil(store) { !$0.isGenerating && $0.messages.last?.text == "second" }

        XCTAssertEqual(store.state.messages.map(\.role), [.user, .assistant])
        XCTAssertEqual(store.state.messages.last?.text, "second")
        XCTAssertEqual(inference.generateCalls.count, 2)
    }

    func test_clear_emptiesThread() async {
        let (store, _) = await readyStore()
        store.send(.inputChanged("hi"))
        store.send(.send)
        await waitUntil(store) { !$0.isGenerating && $0.messages.count == 2 }

        store.send(.clear)

        XCTAssertTrue(store.state.messages.isEmpty)
        XCTAssertNil(store.state.lastMetrics)
    }

    func test_paramsChanged_clampsValues() async {
        let (store, _) = await readyStore()

        store.send(.paramsChanged(GenerationParams(temperature: -1, topK: 0, topP: 5, maxTokens: 0)))

        XCTAssertEqual(store.state.params.temperature, 0)
        XCTAssertEqual(store.state.params.topK, 1)
        XCTAssertEqual(store.state.params.topP, 1)
        XCTAssertEqual(store.state.params.maxTokens, 1)
    }

    func test_systemPrompt_isPassedToGeneration() async {
        let (store, inference) = await readyStore()
        store.send(.systemPromptChanged("You are terse."))

        store.send(.inputChanged("hi"))
        store.send(.send)
        await waitUntil(store) { !$0.isGenerating }

        XCTAssertEqual(inference.generateCalls.last?.systemPrompt, "You are terse.")
    }

    func test_cancel_forwardsToInference() async {
        let (store, inference) = await readyStore()

        store.send(.cancel)

        let deadline = Date().addingTimeInterval(3)
        while inference.cancelCount == 0 && Date() < deadline {
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertEqual(inference.cancelCount, 1)
    }
}
