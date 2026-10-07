import CoreML
import Foundation

/// `InferenceRunner` over a Core ML `.mlpackage` sentence model.
///
/// Assumes Int32 `input_ids` / `attention_mask` / `token_type_ids` /
/// `position_ids` (`[1 x seqLen]`) in and a float `last_hidden_state`
/// (`[1 x seqLen x dim]`) out (MiniLM converted with coremltools).
/// Sentence vectors use mean pooling over masked positions; L2 normalization
/// happens in the shared embedder. Confirm the layout of the bundled model
/// before changing these assumptions.
final class CoreMLRunner: InferenceRunner {
    private let model: MLModel

    init(model: MLModel) {
        self.model = model
    }

    func runEmbedding(ids: [Int32], mask: [Int32], segmentIds: [Int32]) throws -> [Float] {
        let positions = (0..<ids.count).map(Int32.init)
        let provider = try MLDictionaryFeatureProvider(dictionary: [
            "input_ids": MLFeatureValue(multiArray: intMatrix(ids)),
            "attention_mask": MLFeatureValue(multiArray: intMatrix(mask)),
            "token_type_ids": MLFeatureValue(multiArray: intMatrix(segmentIds)),
            "position_ids": MLFeatureValue(multiArray: intMatrix(positions)),
        ])
        let output = try model.prediction(from: provider)
        guard let hidden = output.featureValue(for: "last_hidden_state")?.multiArrayValue else {
            throw CoreMLEmbedderError.missingOutput("last_hidden_state")
        }
        let floats = floatArray(hidden)
        let dim = floats.count / ids.count
        guard dim > 0, floats.count == ids.count * dim else {
            throw CoreMLEmbedderError.shapeMismatch("hidden size \(floats.count) for \(ids.count) tokens")
        }
        return meanPool(hidden: floats, seqLen: ids.count, dim: dim, mask: mask)
    }

    private func intMatrix(_ values: [Int32]) throws -> MLMultiArray {
        let array = try MLMultiArray(shape: [1, values.count] as [NSNumber], dataType: .int32)
        let pointer = UnsafeMutablePointer<Int32>(OpaquePointer(array.dataPointer))
        for (i, value) in values.enumerated() {
            pointer[i] = value
        }
        return array
    }

    private func floatArray(_ array: MLMultiArray) -> [Float] {
        let strides = array.strides.map { $0.intValue }
        let shape = array.shape.map { $0.intValue }
        // [1 x seqLen x dim], stride-aware in case the layout is not dense.
        let seqLen = shape.count > 1 ? shape[shape.count - 2] : 1
        let dim = shape.last ?? 0
        let strideTokens = strides.count > 1 ? strides[strides.count - 2] : dim
        let strideDims = strides.last ?? 1
        let base = array.dataPointer.bindMemory(to: Float.self, capacity: array.count)
        return (0..<seqLen).flatMap { token in
            (0..<dim).map { d in base[token * strideTokens + d * strideDims] }
        }
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

enum CoreMLEmbedderError: Error, Equatable {
    case missingOutput(String)
    case shapeMismatch(String)
    case missingModel(String)
    case missingVocab(String)
}

extension CoreMLEmbedderError: LocalizedError {
    var errorDescription: String? {
        switch self {
        case .missingOutput(let name):
            "model produced no output \(name)"
        case .shapeMismatch(let detail):
            "unexpected model output shape: \(detail)"
        case .missingModel(let name):
            "embedding model missing: \(name) — add the file to the app bundle (see README)"
        case .missingVocab(let name):
            "vocab missing: \(name) — add the file to the app bundle (see README)"
        }
    }
}
