import Foundation
import TensorFlowLite

/// `InferenceRunner` over a raw LiteRT `Interpreter`.
///
/// Assumes a BERT-style embedding model with Int32 `ids`/`mask`/`segment_ids`
/// (`[1 x seqLen]`) inputs — located by tensor *name*, since order varies per
/// file — and a float `[1 x seqLen x dim]` sequence output, mean-pooled here.
/// Confirm the layout of the bundled model with Model Explorer before changing
/// these assumptions.
final class InterpreterRunner: InferenceRunner {
    private let interpreter: Interpreter
    private var inputOrder: (ids: Int, mask: Int, segment: Int)?

    init(interpreter: Interpreter) {
        self.interpreter = interpreter
    }

    func runEmbedding(ids: [Int32], mask: [Int32], segmentIds: [Int32]) throws -> [Float] {
        if inputOrder == nil {
            var names: [String] = []
            for i in 0..<interpreter.inputTensorCount {
                names.append(try interpreter.input(at: i).name)
            }
            inputOrder = try Self.resolveInputOrder(names: names)
        }
        let order = inputOrder!
        try interpreter.copy(data(from: ids), toInputAt: order.ids)
        try interpreter.copy(data(from: mask), toInputAt: order.mask)
        try interpreter.copy(data(from: segmentIds), toInputAt: order.segment)
        try interpreter.invoke()
        let output = try interpreter.output(at: 0)
        let floats = array(from: output.data, as: Float.self)
        let dims = output.shape.dimensions
        guard dims.count == 3 else {
            throw LiteRTEmbedderError.shapeMismatch("rank \(dims.count), expected [1 x seqLen x dim]")
        }
        let (seqLen, dim) = (dims[1], dims[2])
        guard floats.count == seqLen * dim else {
            throw LiteRTEmbedderError.shapeMismatch("hidden size \(floats.count) for \(seqLen) tokens")
        }
        return meanPool(hidden: floats, seqLen: seqLen, dim: dim, mask: mask)
    }

    /// Maps tensor names to (ids, mask, segment) input indices. Pure — unit-tested.
    static func resolveInputOrder(names: [String]) throws -> (ids: Int, mask: Int, segment: Int) {
        guard names.count >= 3 else {
            throw LiteRTEmbedderError.insufficientInputs(names.count)
        }
        var ids: Int?
        var mask: Int?
        var segment: Int?
        for (i, name) in names.enumerated() {
            let lower = name.lowercased()
            if lower.contains("mask") {
                mask = i
            } else if lower.contains("segment") || lower.contains("type") {
                segment = i
            } else if lower.contains("ids") || lower.contains("id") {
                ids = i
            }
        }
        guard let ids, let mask, let segment else {
            throw LiteRTEmbedderError.unknownInputs(names)
        }
        return (ids, mask, segment)
    }
}

/// Masks-average of token vectors. Pure — unit-tested.
func meanPool(hidden: [Float], seqLen: Int, dim: Int, mask: [Int32]) -> [Float] {
    var sums = [Float](repeating: 0, count: dim)
    var count: Float = 0
    for token in 0..<seqLen {
        guard token < mask.count, mask[token] != 0 else { continue }
        count += 1
        let base = token * dim
        for d in 0..<dim {
            sums[d] += hidden[base + d]
        }
    }
    guard count > 0 else { return sums }
    return sums.map { $0 / count }
}

enum LiteRTEmbedderError: Error, Equatable {
    case insufficientInputs(Int)
    case unknownInputs([String])
    case shapeMismatch(String)
    case missingModel(String)
    case missingVocab(String)
}

extension LiteRTEmbedderError: LocalizedError {
    var errorDescription: String? {
        switch self {
        case .insufficientInputs(let count):
            "embedding model has \(count) inputs, expected at least 3 (ids, mask, segment_ids)"
        case .unknownInputs(let names):
            "cannot map model inputs by name: \(names.joined(separator: ", "))"
        case .shapeMismatch(let detail):
            "unexpected model output shape: \(detail)"
        case .missingModel(let name):
            "embedding model missing: \(name) — \(reason)"
        case .missingVocab(let name):
            "vocab missing: \(name) — \(reason)"
        }
    }

    private var reason: String {
        "add the files to the app bundle (see README)"
    }
}

private func data<T>(from array: [T]) -> Data {
    array.withUnsafeBufferPointer { Data(buffer: $0) }
}

private func array<T>(from data: Data, as type: T.Type) -> [T] {
    data.withUnsafeBytes { Array($0.bindMemory(to: type)) }
}
