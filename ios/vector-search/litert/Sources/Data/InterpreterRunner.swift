import Foundation
import TensorFlowLite

/// `InferenceRunner` over a raw LiteRT `Interpreter`.
///
/// Assumes a BERT-style embedding model whose first three Int32 inputs are
/// `ids`, `mask`, `segment_ids` (`[1 x seqLen]`) and whose first output is the
/// float embedding vector (`[1 x N]`). Confirm the layout of the bundled model
/// with Model Explorer before changing these assumptions.
final class InterpreterRunner: InferenceRunner {
    private let interpreter: Interpreter

    init(interpreter: Interpreter) {
        self.interpreter = interpreter
    }

    func runEmbedding(ids: [Int32], mask: [Int32], segmentIds: [Int32]) throws -> [Float] {
        guard interpreter.inputTensorCount >= 3 else {
            throw LiteRTEmbedderError.insufficientInputs(interpreter.inputTensorCount)
        }
        try interpreter.copy(data(from: ids), toInputAt: 0)
        try interpreter.copy(data(from: mask), toInputAt: 1)
        try interpreter.copy(data(from: segmentIds), toInputAt: 2)
        try interpreter.invoke()
        let output = try interpreter.output(at: 0)
        return array(from: output.data, as: Float.self)
    }
}

enum LiteRTEmbedderError: Error, Equatable {
    case insufficientInputs(Int)
    case missingModel(String)
    case missingVocab(String)
}

private func data<T>(from array: [T]) -> Data {
    array.withUnsafeBufferPointer { Data(buffer: $0) }
}

private func array<T>(from data: Data, as type: T.Type) -> [T] {
    data.withUnsafeBytes { Array($0.bindMemory(to: type)) }
}
