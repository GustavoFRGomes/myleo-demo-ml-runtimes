import Foundation

/// Orchestrates one decision: strict encode → token-table gather → one graph pass → marker
/// logits → the author's answer arithmetic. Not thread-safe; the store calls it serially.
final class JuliaEngine: Decider, @unchecked Sendable {
    private let encoder: JuliaEncoding
    private let table: JuliaTokenTable
    private let graph: (Int) throws -> JuliaGraphRunning
    private let availableWindows: [Int]
    private let loadReport: DecisionModelInfo
    private let defaultWindow: Int

    /// Production wiring: real tokenizer, mapped table and the TFLite graphs on disk.
    static func make(files: JuliaModelFiles, preferGPU: Bool = true, allowPrecisionLoss: Bool = false, useLegacy: Bool = false, coreML: Bool = false) async throws -> JuliaEngine {
        let tokenizerStart = Date()
        let encoder = try await JuliaTokenizer(directory: files.directory)
        let tokenizerMs = Date().timeIntervalSince(tokenizerStart) * 1000

        let tableStart = Date()
        let table = try JuliaTokenTable(fileURL: files.tokenTable)
        let tableMs = Date().timeIntervalSince(tableStart) * 1000

        let windows = files.windows
        guard let first = windows.first else {
            throw DecisionError.model("no Julia graph installed")
        }

        // GPU first (FP32, no precision loss); fall back to CPU and report which compiled.
        let graphStart = Date()
        let loaded = try Self.loadRunner(url: files.graphs[first]!, window: first, preferGPU: preferGPU, allowPrecisionLoss: allowPrecisionLoss, useLegacy: useLegacy, coreML: coreML)
        let graphMs = Date().timeIntervalSince(graphStart) * 1000
        var cache: [Int: JuliaGraphRunning] = [first: loaded]

        let report = DecisionModelInfo(
            accelerator: loaded.accelerator,
            defaultWindow: first,
            availableWindows: windows,
            tokenizerLoadMs: tokenizerMs,
            tableLoadMs: tableMs,
            graphLoadMs: graphMs,
            totalLoadMs: tokenizerMs + tableMs + graphMs
        )

        return JuliaEngine(
            encoder: encoder,
            table: table,
            availableWindows: windows,
            defaultWindow: first,
            loadReport: report,
            graph: { window in
                if let cached = cache[window] { return cached }
                guard let url = files.graphs[window] else {
                    throw DecisionError.model("graph for window \(window) is not installed")
                }
                let runner = try Self.loadRunner(url: url, window: window, preferGPU: preferGPU, allowPrecisionLoss: allowPrecisionLoss, useLegacy: useLegacy, coreML: coreML)
                cache[window] = runner
                return runner
            }
        )
    }

    /// Test seam: injected encoder/table/graph, already-loaded report.
    init(
        encoder: JuliaEncoding,
        table: JuliaTokenTable,
        availableWindows: [Int],
        defaultWindow: Int,
        loadReport: DecisionModelInfo,
        graph: @escaping (Int) throws -> JuliaGraphRunning
    ) {
        self.encoder = encoder
        self.table = table
        self.graph = graph
        self.availableWindows = availableWindows
        self.defaultWindow = defaultWindow
        self.loadReport = loadReport
    }

    func load() async throws -> DecisionModelInfo { loadReport }

    func decide(_ request: DecisionRequest) async throws -> DecisionOutcome {
        let started = Date()

        var window = defaultWindow
        var builder = try JuliaSequenceBuilder(encoder: encoder, window: window)
        var sequence: JuliaSequence
        do {
            sequence = try builder.build(state: request.state, question: request.question)
        } catch let error as DecisionError {
            // A request over the small window may still fit the optional 1024 graph.
            if case .encoding = error, let larger = availableWindows.first(where: { $0 > window }) {
                window = larger
                builder = try JuliaSequenceBuilder(encoder: encoder, window: window)
                sequence = try builder.build(state: request.state, question: request.question)
            } else {
                throw error
            }
        }
        let prepared = Date()

        let runner = try graph(window)
        var embeddings = [Float](repeating: 0, count: window * JuliaTokenTable.width)
        try table.gather(ids: sequence.ids, window: window, into: &embeddings)
        let looked = Date()

        var mask = [Float](repeating: 0, count: window)
        for index in 0..<sequence.tokenCount { mask[index] = 1 }
        var onehot = [Float](repeating: 0, count: 3)
        onehot[sequence.qtype] = 1

        let run = try runner.run(embeddings: embeddings, attentionMask: mask, qtypeOnehot: onehot)
        let decodedStart = Date()

        let markerLogits = try JuliaDecoder.gather(tokenLogits: run.tokenLogits, markers: sequence.markers)
        let decoded = try JuliaDecoder.decode(markerLogits: markerLogits, question: request.question)
        let done = Date()

        let timings = DecisionTimings(
            prepareMs: prepared.timeIntervalSince(started) * 1000,
            lookupMs: looked.timeIntervalSince(prepared) * 1000,
            compileMs: 0,
            writeMs: run.writeMs,
            enqueueMs: run.enqueueMs,
            readbackMs: run.readbackMs,
            decodeMs: done.timeIntervalSince(decodedStart) * 1000
        )

        return DecisionOutcome(
            question: request.question,
            probabilities: decoded.probabilities,
            choiceId: decoded.choiceId,
            score: decoded.score,
            noul: decoded.noul,
            maxProbability: decoded.maxProbability,
            argmax: decoded.argmax,
            tokenCount: sequence.tokenCount,
            window: window,
            accelerator: runner.accelerator,
            timings: timings
        )
    }

    private static func loadRunner(
        url: URL,
        window: Int,
        preferGPU: Bool,
        allowPrecisionLoss: Bool,
        useLegacy: Bool,
        coreML: Bool
    ) throws -> JuliaGraphRunning {
        if coreML {
            do {
                let runner = try LiteRtGraphRunner.make(
                    graphURL: url, window: window, accelerator: .coreML,
                    allowPrecisionLoss: allowPrecisionLoss
                )
                print("DECISION|graph|runtime=2.17|window=\(window)|accelerator=coreml")
                return runner
            } catch {
                print("DECISION|graph|window=\(window)|coreml failed: \(error.localizedDescription)")
            }
        }

        if !useLegacy {
            if preferGPU {
                do {
                    let gpu = try LiteRt2GraphRunner.make(graphURL: url, window: window, accelerator: .gpu)
                    print("DECISION|graph|runtime=2.3|window=\(window)|accelerator=gpu")
                    return gpu
                } catch {
                    print("DECISION|graph|runtime=2.3|window=\(window)|gpu failed: \(error.localizedDescription)")
                }
            }
            if let cpu = try? LiteRt2GraphRunner.make(graphURL: url, window: window, accelerator: .cpu) {
                print("DECISION|graph|runtime=2.3|window=\(window)|accelerator=cpu")
                return cpu
            }
            print("DECISION|graph|runtime=2.3|window=\(window)|cpu failed, trying 2.17")
        }

        if preferGPU {
            do {
                let gpu = try LiteRtGraphRunner.make(graphURL: url, window: window, accelerator: .gpu, allowPrecisionLoss: allowPrecisionLoss)
                print("DECISION|graph|runtime=2.17|window=\(window)|accelerator=gpu")
                return gpu
            } catch let error as DecisionError {
                // A model/shape problem is not a Metal problem: report it instead of
                // silently recompiling the same graph on the CPU.
                if case .model = error { throw error }
                print("DECISION|graph|window=\(window)|gpu failed: \(error.localizedDescription)")
            } catch {
                print("DECISION|graph|window=\(window)|gpu unavailable: \(error.localizedDescription)")
            }
        }
        let cpu = try LiteRtGraphRunner.make(graphURL: url, window: window, accelerator: .cpu)
        print("DECISION|graph|window=\(window)|accelerator=cpu")
        return cpu
    }
}
