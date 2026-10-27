import Foundation

/// Julia-1 on ONNX Runtime: strict encode → ids + absolute marker positions → one session run →
/// the same decoder as the LiteRT lane. No host token table; the export embeds it.
final class OnnxJuliaEngine: Decider, @unchecked Sendable {
    static let window = 1024
    static let headLength = 512

    private let encoder: JuliaEncoding
    private let runner: OnnxCGraphRunner
    private let report: DecisionModelInfo

    static func make(files: OnnxJuliaFiles) async throws -> OnnxJuliaEngine {
        let tokenizerStart = Date()
        let encoder = try await JuliaTokenizer(directory: files.directory)
        let tokenizerMs = Date().timeIntervalSince(tokenizerStart) * 1000

        let graphStart = Date()
        let runner = try OnnxCGraphRunner(modelPath: files.model.path)
        let graphMs = Date().timeIntervalSince(graphStart) * 1000

        let report = DecisionModelInfo(
            accelerator: .cpu,
            defaultWindow: window,
            availableWindows: [window],
            tokenizerLoadMs: tokenizerMs,
            tableLoadMs: 0,
            graphLoadMs: graphMs,
            totalLoadMs: tokenizerMs + graphMs
        )
        return OnnxJuliaEngine(encoder: encoder, runner: runner, report: report)
    }

    init(encoder: JuliaEncoding, runner: OnnxCGraphRunner, report: DecisionModelInfo) {
        self.encoder = encoder
        self.runner = runner
        self.report = report
    }

    func load() async throws -> DecisionModelInfo { report }

    func decide(_ request: DecisionRequest) async throws -> DecisionOutcome {
        let started = Date()
        let builder = try JuliaSequenceBuilder(
            encoder: encoder,
            window: Self.window,
            headLength: Self.headLength
        )
        let sequence = try builder.build(state: request.state, question: request.question)
        let prepared = Date()

        let mask = [Int](repeating: 1, count: sequence.tokenCount)
        let run = try runner.run(
            ids: sequence.ids,
            mask: mask,
            markers: sequence.markers,
            qtype: sequence.qtype
        )
        let decodedStart = Date()
        let decoded = try JuliaDecoder.decode(markerLogits: run.logits.map(Double.init), question: request.question)
        let done = Date()

        let timings = DecisionTimings(
            prepareMs: prepared.timeIntervalSince(started) * 1000,
            lookupMs: 0,
            compileMs: 0,
            writeMs: run.writeMs,
            enqueueMs: run.runMs,
            readbackMs: run.readMs,
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
            window: Self.window,
            accelerator: .cpu,
            timings: timings
        )
    }
}
