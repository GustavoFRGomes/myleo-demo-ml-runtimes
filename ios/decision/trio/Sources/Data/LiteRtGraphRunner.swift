import Foundation
import TensorFlowLite

/// One graph call: the per-token logits plus the write/enqueue/readback split.
struct JuliaGraphRun: Sendable {
    let tokenLogits: [Float]
    let writeMs: Double
    let enqueueMs: Double
    let readbackMs: Double
}

/// The inference seam: gathered fp32 embeddings, attention mask and one-hot question type in;
/// token logits out. A fake implementation tests the engine's sequencing without a 185 MB graph.
protocol JuliaGraphRunning: AnyObject, Sendable {
    var window: Int { get }
    var accelerator: DecisionAccelerator { get }
    func run(embeddings: [Float], attentionMask: [Float], qtypeOnehot: [Float]) throws -> JuliaGraphRun
}

/// `JuliaGraphRunning` over the TFLite `Interpreter` and the `serving_default` signature.
///
/// The GPU path explicitly keeps **precision loss disabled**: the checkpoint's answers change
/// under fp16 arithmetic, so `MetalDelegate.Options.isPrecisionLossAllowed` stays false. Creation
/// does not silently fall back — the caller decides which accelerator to try and reports the one
/// that compiled.
final class LiteRtGraphRunner: JuliaGraphRunning, @unchecked Sendable {
    static let inputsEmbeds = "inputs_embeds"
    static let attentionMask = "attention_mask"
    static let qtypeOnehot = "qtype_onehot"
    static let outputName = "token_logits"

    private let interpreter: Interpreter
    private let inputIndex: [String: Int]
    private let outputIndex: Int
    let window: Int
    let accelerator: DecisionAccelerator

    private init(interpreter: Interpreter, window: Int, accelerator: DecisionAccelerator) throws {
        self.interpreter = interpreter
        self.window = window
        self.accelerator = accelerator

        // Signature-qualified graphs expose names like `serving_default_inputs_embeds`,
        // so match on the suffix while keeping the caller's plain names.
        var index: [String: Int] = [:]
        for i in 0..<interpreter.inputTensorCount {
            index[try interpreter.input(at: i).name] = i
        }
        func resolve(_ expected: String) -> Int? {
            if let exact = index[expected] { return exact }
            return index.first { $0.key.hasSuffix(expected) }?.value
        }
        var resolved: [String: Int] = [:]
        for name in [Self.inputsEmbeds, Self.attentionMask, Self.qtypeOnehot] {
            guard let position = resolve(name) else {
                throw DecisionError.model("graph is missing input \(name); has \(index.keys.sorted())")
            }
            resolved[name] = position
        }
        index = resolved
        self.inputIndex = index

        var outputNames: [String] = []
        var output: Int?
        for i in 0..<interpreter.outputTensorCount {
            let name = try interpreter.output(at: i).name
            outputNames.append(name)
            if name == Self.outputName || name.hasSuffix(Self.outputName) { output = i }
        }
        // Single-output graphs are unambiguous even when the exporter renamed the tensor.
        if output == nil, outputNames.count == 1 { output = 0 }
        guard let output else {
            throw DecisionError.model("graph is missing output \(Self.outputName); has \(outputNames)")
        }
        if outputNames.count == 1, outputNames[0] != Self.outputName {
            print("DECISION|graph|output=\(outputNames[0])")
        }
        self.outputIndex = output
    }

    /// Tries the requested accelerator; the caller falls back by calling again with `.cpu`.
    static func make(graphURL: URL, window: Int, accelerator: DecisionAccelerator, allowPrecisionLoss: Bool = false) throws -> LiteRtGraphRunner {
        var delegates: [Delegate] = []
        if accelerator == .gpu {
            var options = MetalDelegate.Options()
            options.isPrecisionLossAllowed = allowPrecisionLoss
            delegates = [MetalDelegate(options: options)]
        } else if accelerator == .coreML {
            var options = CoreMLDelegate.Options()
            // Core ML decides per op; `.all` lets it use the Neural Engine and GPU.
            options.enabledDevices = .all
            guard let delegate = CoreMLDelegate(options: options) else {
                throw DecisionError.model("Core ML delegate could not be created")
            }
            delegates = [delegate]
        }
        var options = Interpreter.Options()
        // Android runs the CPU lane with 4 intra-op threads; match it so the two
        // platforms' timings describe the same setup.
        options.threadCount = 4
        // XNNPACK is *off* by default in this pod; without it the interpreter runs
        // reference kernels (measured ~10x slower on this encoder).
        options.isXNNPackEnabled = true
        let interpreter = try Interpreter(modelPath: graphURL.path, options: options, delegates: delegates.isEmpty ? nil : delegates)
        try interpreter.allocateTensors()
        return try LiteRtGraphRunner(interpreter: interpreter, window: window, accelerator: accelerator)
    }

    func run(embeddings: [Float], attentionMask: [Float], qtypeOnehot: [Float]) throws -> JuliaGraphRun {
        guard embeddings.count == window * JuliaTokenTable.width else {
            throw DecisionError.graph("expected \(window * JuliaTokenTable.width) embedding values")
        }
        guard attentionMask.count == window else {
            throw DecisionError.graph("expected \(window) attention values")
        }
        guard qtypeOnehot.count == 3 else {
            throw DecisionError.graph("expected a 3-wide qtype one-hot")
        }

        let writeStart = DispatchTime.now().uptimeNanoseconds
        try interpreter.copy(Data(bytes: embeddings, count: embeddings.count * 4), toInputAt: inputIndex[Self.inputsEmbeds]!)
        try interpreter.copy(Data(bytes: attentionMask, count: attentionMask.count * 4), toInputAt: inputIndex[Self.attentionMask]!)
        try interpreter.copy(Data(bytes: qtypeOnehot, count: qtypeOnehot.count * 4), toInputAt: inputIndex[Self.qtypeOnehot]!)
        let written = DispatchTime.now().uptimeNanoseconds

        try interpreter.invoke()
        let enqueued = DispatchTime.now().uptimeNanoseconds

        let output = try interpreter.output(at: outputIndex)
        let logits: [Float] = output.data.withUnsafeBytes { raw in
            Array(raw.bindMemory(to: Float.self))
        }
        let read = DispatchTime.now().uptimeNanoseconds

        guard logits.count == window else {
            throw DecisionError.graph("unexpected token_logits size \(logits.count), expected \(window)")
        }
        return JuliaGraphRun(
            tokenLogits: logits,
            writeMs: Self.ms(written - writeStart),
            enqueueMs: Self.ms(enqueued - written),
            readbackMs: Self.ms(read - enqueued)
        )
    }

    private static func ms(_ nanos: UInt64) -> Double { Double(nanos) / 1_000_000 }
}
