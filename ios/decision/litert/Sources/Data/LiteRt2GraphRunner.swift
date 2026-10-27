import CLiteRT
import Foundation

/// `JuliaGraphRunning` over LiteRT **2.3** (C API + Metal accelerator).
///
/// Replaces the 2.17 `Interpreter` path, whose Metal delegate cannot compile this graph.
/// Inputs are host-memory tensor buffers ([1×window×384] embeddings, [1×window] mask,
/// [1×3] qtype); the output is [1×window] token logits. The 2.17 runner stays in the leaf as
/// `LiteRtGraphRunner` for the "older runtime" benchmark figures.
final class LiteRt2GraphRunner: JuliaGraphRunning, @unchecked Sendable {
    let window: Int
    let accelerator: DecisionAccelerator

    private var env: LiteRtEnvironment?
    private var model: LiteRtModel?
    private var compiled: LiteRtCompiledModel?
    private var inputBuffers: [LiteRtTensorBuffer?] = []
    private var outputBuffer: LiteRtTensorBuffer?
    private var inputMemory: [UnsafeMutableRawPointer] = []
    private var outputMemory: UnsafeMutableRawPointer?

    private init(window: Int, accelerator: DecisionAccelerator) {
        self.window = window
        self.accelerator = accelerator
    }

    static func make(graphURL: URL, window: Int, accelerator: DecisionAccelerator) throws -> LiteRt2GraphRunner {
        let runner = LiteRt2GraphRunner(window: window, accelerator: accelerator)
        do {
            try runner.build(graphURL: graphURL)
            return runner
        } catch {
            runner.destroy()
            throw error
        }
    }

    private func build(graphURL: URL) throws {
        var env: LiteRtEnvironment?
        try Self.check(LiteRtCreateEnvironment(0, nil, &env))
        self.env = env

        var model: LiteRtModel?
        try Self.check(LiteRtCreateModelFromFile(env, graphURL.path, &model))
        self.model = model

        var options: LiteRtOptions?
        try Self.check(LiteRtCreateOptions(&options))
        defer { LiteRtDestroyOptions(options) }
        let accelerators: LiteRtHwAcceleratorSet = accelerator == .gpu
            ? (kLiteRtHwAcceleratorGpu.rawValue | kLiteRtHwAcceleratorCpu.rawValue)
            : kLiteRtHwAcceleratorCpu.rawValue
        try Self.check(LiteRtSetOptionsHardwareAccelerators(options, accelerators))

        var compiled: LiteRtCompiledModel?
        try Self.check(LiteRtCreateCompiledModel(env, model, options, &compiled))
        self.compiled = compiled

        // Inputs, in the graph's signature order used by the Android contract.
        inputBuffers = [
            try buffer(shape: [1, window, JuliaTokenTable.width]),
            try buffer(shape: [1, window]),
            try buffer(shape: [1, 3]),
        ]
        outputBuffer = try buffer(shape: [1, window])

        // The first GPU invocation compiles kernels; warm it so timed decisions describe
        // steady state (the same reason the Android suite reports a first-load cost).
        _ = try run(
            embeddings: [Float](repeating: 0, count: window * JuliaTokenTable.width),
            attentionMask: [Float](repeating: 0, count: window),
            qtypeOnehot: [0, 0, 0]
        )
    }

    private func buffer(shape: [Int]) throws -> LiteRtTensorBuffer? {
        let count = shape.reduce(1, *)
        let bytes = count * MemoryLayout<Float>.size
        let memory = UnsafeMutableRawPointer.allocate(byteCount: bytes, alignment: 64)
        inputMemory.append(memory) // released in destroy(); output memory uses the same list

        var type = LiteRtRankedTensorType()
        type.element_type = kLiteRtElementTypeFloat32
        type.layout.rank = UInt32(shape.count)
        withUnsafeMutablePointer(to: &type.layout.dimensions) { pointer in
            pointer.withMemoryRebound(to: Int32.self, capacity: 8) { dimensions in
                for (index, value) in shape.enumerated() { dimensions[index] = Int32(value) }
            }
        }

        var buffer: LiteRtTensorBuffer?
        try Self.check(LiteRtCreateTensorBufferFromHostMemory(&type, memory, bytes, nil, &buffer))
        return buffer
    }

    func run(embeddings: [Float], attentionMask: [Float], qtypeOnehot: [Float]) throws -> JuliaGraphRun {
        guard embeddings.count == window * JuliaTokenTable.width else {
            throw DecisionError.graph("expected \(window * JuliaTokenTable.width) embedding values")
        }
        guard attentionMask.count == window, qtypeOnehot.count == 3, inputMemory.count >= 4 else {
            throw DecisionError.graph("input shape mismatch")
        }

        let writeStart = DispatchTime.now().uptimeNanoseconds
        Self.copy(embeddings, into: inputMemory[0])
        Self.copy(attentionMask, into: inputMemory[1])
        Self.copy(qtypeOnehot, into: inputMemory[2])
        let written = DispatchTime.now().uptimeNanoseconds

        var inputs = [inputBuffers[0], inputBuffers[1], inputBuffers[2]]
        var outputs = [outputBuffer]
        try inputs.withUnsafeMutableBufferPointer { inputPointer in
            try outputs.withUnsafeMutableBufferPointer { outputPointer in
                try Self.check(
                    LiteRtRunCompiledModel(compiled, 0, 3, inputPointer.baseAddress, 1, outputPointer.baseAddress)
                )
            }
        }
        let ran = DispatchTime.now().uptimeNanoseconds

        var host: UnsafeMutableRawPointer?
        try Self.check(LiteRtLockTensorBuffer(outputBuffer, &host, kLiteRtTensorBufferLockModeRead))
        guard let host else { throw DecisionError.graph("output buffer has no host memory") }
        let logits = Array(UnsafeBufferPointer(start: host.assumingMemoryBound(to: Float.self), count: window))
        LiteRtUnlockTensorBuffer(outputBuffer)
        let read = DispatchTime.now().uptimeNanoseconds

        return JuliaGraphRun(
            tokenLogits: logits,
            writeMs: Self.ms(written - writeStart),
            enqueueMs: Self.ms(ran - written),
            readbackMs: Self.ms(read - ran)
        )
    }

    private static func copy(_ values: [Float], into destination: UnsafeMutableRawPointer) {
        values.withUnsafeBytes { source in
            destination.copyMemory(from: source.baseAddress!, byteCount: source.count)
        }
    }

    private func destroy() {
        for buffer in inputBuffers { if let buffer { LiteRtDestroyTensorBuffer(buffer) } }
        if let outputBuffer { LiteRtDestroyTensorBuffer(outputBuffer) }
        for memory in inputMemory { memory.deallocate() }
        inputMemory.removeAll()
        inputBuffers.removeAll()
        outputBuffer = nil
        if let compiled { LiteRtDestroyCompiledModel(compiled) }
        if let model { LiteRtDestroyModel(model) }
        if let env { LiteRtDestroyEnvironment(env) }
        compiled = nil
        model = nil
        env = nil
    }

    deinit { destroy() }

    private static func check(_ status: LiteRtStatus) throws {
        guard status == kLiteRtStatusOk else {
            throw DecisionError.graph("LiteRT status \(status)")
        }
    }

    private static func ms(_ nanos: UInt64) -> Double { Double(nanos) / 1_000_000 }
}
