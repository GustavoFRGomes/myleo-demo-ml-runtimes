import Foundation

/// `InferenceRunner` over ONNX Runtime (`ORTSession`).
///
/// Assumes a BERT-style sentence model with Int64 `input_ids` /
/// `attention_mask` / `token_type_ids` (`[1 x seqLen]`) in and a float
/// `last_hidden_state` (`[1 x seqLen x dim]`) out (e.g. MiniLM-L6-v2).
/// Sentence vectors use mean pooling over masked positions; L2 normalization
/// happens in the shared embedder. Confirm the layout of the bundled model
/// before changing these assumptions.
final class OnnxRunner: InferenceRunner {
    private let session: ORTSession

    init(session: ORTSession) {
        self.session = session
    }

    func runEmbedding(ids: [Int32], mask: [Int32], segmentIds: [Int32]) throws -> [Float] {
        let inputs: [String: ORTValue] = [
            "input_ids": try int64Tensor(ids.map(Int64.init)),
            "attention_mask": try int64Tensor(mask.map(Int64.init)),
            "token_type_ids": try int64Tensor(segmentIds.map(Int64.init)),
        ]
        let outputs = try session.run(
            withInputs: inputs,
            outputNames: ["last_hidden_state"],
            runOptions: nil
        )
        guard let hidden = outputs["last_hidden_state"] else {
            throw OnnxEmbedderError.missingOutput("last_hidden_state")
        }
        let floats = try floatArray(hidden)
        let dim = floats.count / ids.count
        guard dim > 0, floats.count == ids.count * dim else {
            throw OnnxEmbedderError.shapeMismatch("hidden size \(floats.count) for \(ids.count) tokens")
        }
        return meanPool(hidden: floats, seqLen: ids.count, dim: dim, mask: mask)
    }

    private func int64Tensor(_ values: [Int64]) throws -> ORTValue {
        let data = values.withUnsafeBytes {
            NSMutableData(bytes: $0.baseAddress!, length: $0.count)
        }
        return try ORTValue(
            tensorData: data,
            elementType: .int64,
            shape: [1, values.count].map { NSNumber(value: $0) }
        )
    }

    private func floatArray(_ value: ORTValue) throws -> [Float] {
        let data = try value.tensorData()
        let bytes = Data(bytes: data.bytes, count: data.length)
        return bytes.withUnsafeBytes { Array($0.bindMemory(to: Float.self)) }
    }
}

/// Mean pooling lives in InterpreterRunner.swift (shared by all three runners).

enum OnnxEmbedderError: Error, Equatable {
    case missingOutput(String)
    case shapeMismatch(String)
    case missingModel(String)
    case missingVocab(String)
}

extension OnnxEmbedderError: LocalizedError {
    var errorDescription: String? {
        switch self {
        case .missingOutput(let name):
            "model produced no output tensor \(name)"
        case .shapeMismatch(let detail):
            "unexpected model output shape: \(detail)"
        case .missingModel(let name):
            "embedding model missing: \(name) — add the file to the app bundle (see README)"
        case .missingVocab(let name):
            "vocab missing: \(name) — add the file to the app bundle (see README)"
        }
    }
}
