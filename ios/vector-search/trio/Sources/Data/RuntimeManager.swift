import Foundation
import os

/// A loaded runtime: usable embedder plus its load report for the stats sheet.
///
/// Marked unchecked-Sendable: the embedder wraps a native runtime object, but
/// the manager hands each loaded embedder to exactly one consumer pipeline at
/// a time (one ingest/search task per runtime), matching the sibling leafs.
struct LoadedRuntime: @unchecked Sendable {
    let embedder: any Embedder
    let modelName: String
    let modelLoadMs: Int64
}

/// Owns the active `InferenceRuntime` and lazily loads each runtime's model on
/// first selection (never at startup). Loaded runtimes stay cached for the
/// session so re-selecting is instant.
///
/// The index is shared between runtimes and holds one runtime's vectors at a
/// time: selecting a different runtime clears it first. The `load` /
/// `hasModelAssets` seams are injected so unit tests never touch the bundle
/// or native runtimes.
///
/// Unchecked-Sendable with an NSLock around cached state: the async `select`
/// hops executors for the blocking load, so the compiler must be able to
/// transfer the manager. In practice all calls serialize on the main actor
/// (app) or a single test thread; the lock additionally guards the sync
/// cache paths and never spans the await.
final class RuntimeManager: @unchecked Sendable {
    private struct State {
        var current: InferenceRuntime?
        var loaded: [InferenceRuntime: LoadedRuntime] = [:]
    }

    private let lock = NSLock()
    private var state = State()

    var current: InferenceRuntime? {
        lock.withLock { state.current }
    }

    let documents: DocumentRepository
    private let load: @Sendable (InferenceRuntime) throws -> LoadedRuntime
    private let hasModelAssets: @Sendable (InferenceRuntime) -> Bool
    private let log = Logger(subsystem: "org.ggomes.myleo.vectorsearch.trio", category: "Runtime")

    init(
        documents: DocumentRepository,
        load: @escaping @Sendable (InferenceRuntime) throws -> LoadedRuntime = RuntimeManager.bundleLoad,
        hasModelAssets: @escaping @Sendable (InferenceRuntime) -> Bool = RuntimeManager.bundleHasAssets
    ) {
        self.documents = documents
        self.load = load
        self.hasModelAssets = hasModelAssets
    }

    func isLoaded(_ runtime: InferenceRuntime) -> Bool {
        lock.withLock { state.loaded[runtime] != nil }
    }

    func hasBundledModel(_ runtime: InferenceRuntime) -> Bool {
        hasModelAssets(runtime)
    }

    func currentLoaded() -> LoadedRuntime? {
        lock.withLock { state.current.flatMap { state.loaded[$0] } }
    }

    /// Use case over the active runtime. Throws when none is selected yet.
    func useCase() throws -> CalculateEmbeddingUseCase {
        guard let runtime = current, let embedder = lock.withLock({ state.loaded[runtime]?.embedder }) else {
            throw TrioError.noRuntimeSelected
        }
        return CalculateEmbeddingUseCase(repository: DefaultEmbeddingRepository(embedder: embedder))
    }

    /// Selects `runtime`, loading its model on first use (off the caller's
    /// thread). Switching to a different runtime clears the shared index
    /// first. Failures (e.g. missing model files) leave the previous
    /// selection untouched.
    @discardableResult
    func select(_ runtime: InferenceRuntime) async throws -> LoadedRuntime {
        if let cached = lock.withLock({ state.loaded[runtime] }) {
            if current != runtime {
                try documents.clear()
                log.debug("switched to \(runtime.label), index cleared")
                lock.withLock { state.current = runtime }
            }
            return cached
        }
        // Snapshot the pre-load selection: no lock spans the await below, so
        // the commit compares against this (a concurrent select could
        // double-load, but last-write-wins keeps the cache valid).
        let previous = current
        // Read the loader on the caller's isolation so the detached task only
        // carries the (Sendable) closure value, never self.
        let loader = self.load
        let fresh: LoadedRuntime
        do {
            let start = Date()
            let created = try await Task.detached(priority: .userInitiated) {
                try loader(runtime)
            }.value
            fresh = LoadedRuntime(
                embedder: created.embedder,
                modelName: created.modelName,
                modelLoadMs: Int64(Date().timeIntervalSince(start) * 1000)
            )
        } catch {
            log.warning("\(runtime.label) load failed: \(error.localizedDescription)")
            throw error
        }
        if previous != nil, previous != runtime {
            try documents.clear()
            log.debug("switched to \(runtime.label), index cleared")
        }
        lock.withLock {
            state.loaded[runtime] = fresh
            state.current = runtime
        }
        log.debug("\(runtime.label) model loaded in \(fresh.modelLoadMs)ms")
        return fresh
    }

    // MARK: - Bundle loading (production)

    // Note: .mlpackage probes use the compiled .mlmodelc first — Xcode only
    // ships the compiled model, and the source package may be absent at runtime.
    // A stream probe (not URL resource lookup alone) would fail on directories,
    // so existence here means "a loadable model URL exists".
    static func bundleHasAssets(_ runtime: InferenceRuntime) -> Bool {
        let bundle = Bundle.main
        switch runtime {
        case .litert:
            return bundle.path(forResource: "embedding", ofType: "tflite") != nil
                && bundle.path(forResource: "vocab", ofType: "txt") != nil
        case .onnx:
            return bundle.path(forResource: "embedding", ofType: "onnx") != nil
                && bundle.path(forResource: "vocab", ofType: "txt") != nil
        case .coreml:
            return (bundle.url(forResource: "embedding", withExtension: "mlmodelc")
                ?? bundle.url(forResource: "embedding", withExtension: "mlpackage")) != nil
                && bundle.path(forResource: "vocab", ofType: "txt") != nil
        }
    }

    static func bundleLoad(_ runtime: InferenceRuntime) throws -> LoadedRuntime {
        switch runtime {
        case .litert:
            let embedder = try LiteRTEmbedderFactory.create()
            return LoadedRuntime(embedder: embedder, modelName: "embedding.tflite", modelLoadMs: 0)
        case .onnx:
            let embedder = try OnnxEmbedderFactory.create()
            return LoadedRuntime(embedder: embedder, modelName: "embedding.onnx", modelLoadMs: 0)
        case .coreml:
            let embedder = try CoreMLEmbedderFactory.create()
            return LoadedRuntime(embedder: embedder, modelName: "embedding.mlpackage", modelLoadMs: 0)
        }
    }
}

enum TrioError: Error, Equatable {
    case noRuntimeSelected
}

extension TrioError: LocalizedError {
    var errorDescription: String? {
        "no runtime selected"
    }
}
