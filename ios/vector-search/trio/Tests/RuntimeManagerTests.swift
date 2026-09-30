import XCTest
@testable import MyLeoVSTrio

final class InferenceRuntimeTests: XCTestCase {
    func testLabels_areDistinct() {
        let labels = InferenceRuntime.allCases.map(\.label)
        XCTAssertEqual(labels, ["LiteRT", "ONNX", "Core ML"])
    }

    func testModelFiles_haveDistinctExtensions() {
        let files = InferenceRuntime.allCases.map(\.modelFile)
        XCTAssertEqual(files, ["embedding.tflite", "embedding.onnx", "embedding.mlpackage"])
    }

    func testDimensions_areSharedMiniLMWidth() {
        XCTAssertEqual(InferenceRuntime.dimensions, 384)
    }
}

private func stubEmbedder(_ vector: [Float]) -> FakeEmbedder {
    let embedder = FakeEmbedder()
    embedder.vector = vector
    return embedder
}

final class RuntimeManagerTests: XCTestCase {
    func testSelect_firstUse_loadsAndSetsCurrent() async throws {
        let manager = RuntimeManager(
            documents: FakeDocuments(),
            load: { runtime in
                LoadedRuntime(
                    embedder: stubEmbedder([0.6, 0.8]),
                    modelName: runtime.modelFile,
                    modelLoadMs: 7
                )
            }
        )

        let loaded = try await manager.select(.litert)

        XCTAssertEqual(loaded.modelName, "embedding.tflite")
        XCTAssertEqual(manager.current, .litert)
        XCTAssertNoThrow(try manager.useCase())
    }

    func testSelect_twice_loadsOnlyOnce() async throws {
        let box = LoadBox()
        let manager = RuntimeManager(
            documents: FakeDocuments(),
            load: { runtime in
                box.increment(runtime)
                return LoadedRuntime(embedder: stubEmbedder([0.1]), modelName: "m", modelLoadMs: 1)
            }
        )

        try await manager.select(.onnx)
        try await manager.select(.onnx)

        XCTAssertEqual(box.counts[.onnx], 1)
    }

    func testSelect_differentRuntime_clearsSharedIndex() async throws {
        let documents = FakeDocuments()
        let manager = RuntimeManager(
            documents: documents,
            load: { _ in LoadedRuntime(embedder: stubEmbedder([0.1]), modelName: "m", modelLoadMs: 1) }
        )
        try await manager.select(.litert)
        try documents.ingest(document: "Document 1", chunks: [ChunkEmbedding(text: "a", vector: [0.1], source: "a")])
        XCTAssertEqual(try documents.stats().chunks, 1)

        try await manager.select(.onnx)

        XCTAssertEqual(manager.current, .onnx)
        XCTAssertEqual(try documents.stats().chunks, 0)
    }

    func testSelect_sameRuntime_doesNotClearIndex() async throws {
        let documents = FakeDocuments()
        let manager = RuntimeManager(
            documents: documents,
            load: { _ in LoadedRuntime(embedder: stubEmbedder([0.1]), modelName: "m", modelLoadMs: 1) }
        )
        try await manager.select(.litert)
        try documents.ingest(document: "Document 1", chunks: [ChunkEmbedding(text: "a", vector: [0.1], source: "a")])

        try await manager.select(.litert)

        XCTAssertEqual(try documents.stats().chunks, 1)
    }

    func testSelect_loadFailure_keepsPreviousSelection() async throws {
        let documents = FakeDocuments()
        let manager = RuntimeManager(
            documents: documents,
            load: { runtime in
                if runtime == .onnx { throw TrioError.noRuntimeSelected }
                return LoadedRuntime(embedder: stubEmbedder([0.1]), modelName: "m", modelLoadMs: 1)
            }
        )
        try await manager.select(.litert)

        do {
            try await manager.select(.onnx)
            XCTFail("expected select to throw")
        } catch {
            XCTAssertEqual(error as? TrioError, .noRuntimeSelected)
        }
        XCTAssertEqual(manager.current, .litert)
        XCTAssertEqual(try documents.stats().chunks, 0)
    }

    func testUseCase_beforeSelection_throws() {
        let manager = RuntimeManager(documents: FakeDocuments(), load: { _ in
            LoadedRuntime(embedder: stubEmbedder([0.1]), modelName: "m", modelLoadMs: 1)
        })

        XCTAssertThrowsError(try manager.useCase())
    }
}

private final class LoadBox: @unchecked Sendable {
    private let lock = NSLock()
    private var _counts: [InferenceRuntime: Int] = [:]
    var counts: [InferenceRuntime: Int] {
        lock.withLock { _counts }
    }
    func increment(_ runtime: InferenceRuntime) {
        lock.withLock { _counts[runtime, default: 0] += 1 }
    }
}
