import Foundation
import TensorFlowLite
import os

/// Builds the production embedding stack from the app bundle (model + vocab
/// bundled with the app — never needed in unit tests).
///
/// Expected bundle resources:
/// - `embedding.tflite` — MiniLM sentence model (Int32 inputs located by
///   tensor name, float `[1 x seqLen x dim]` sequence out, mean-pooled).
/// - `vocab.txt` — one WordPiece token per line.
///
/// Acceleration: the Core ML delegate (Neural Engine) is attempted first. A
/// single warmup inference at load proves the delegate actually executes this
/// graph — delegate/graph mismatches surface as invoke failures, not as
/// interpreter-creation failures, so without the warmup every later embed
/// would fail with a misleading "Must call allocateTensors()." On any warmup
/// failure the factory rebuilds a CPU interpreter instead. The winning path
/// is logged at info level so device logs show which one ran.
enum LiteRTEmbedderFactory {
    static let defaultModelName = "embedding"
    static let defaultVocabName = "vocab"
    static let defaultModelFileName = "embedding.tflite"

    /// Which accelerator to attempt. `.coreml` falls back to CPU automatically.
    enum Accelerator {
        case cpu
        case coreml
    }

    private static let log = Logger(
        subsystem: "org.ggomes.myleo.vectorsearch.trio",
        category: "LiteRT"
    )

    static func create(
        bundle: Bundle = .main,
        modelName: String = defaultModelName,
        vocabName: String = defaultVocabName,
        maxSeqLen: Int = LiteRTEmbedder.defaultMaxSeqLen,
        threadCount: Int? = nil,
        accelerator: Accelerator = .coreml
    ) throws -> LiteRTEmbedder {
        guard let modelPath = bundle.path(forResource: modelName, ofType: "tflite") else {
            throw LiteRTEmbedderError.missingModel("\(modelName).tflite")
        }
        var options = Interpreter.Options()
        if let threadCount {
            options.threadCount = threadCount
        }
        let interpreter: Interpreter
        switch accelerator {
        case .cpu:
            interpreter = try buildCPUInterpreter(modelPath: modelPath, options: options)
        case .coreml:
            do {
                guard let ane = CoreMLDelegate() else {
                    throw LiteRTEmbedderError.delegateUnavailable("no Neural Engine")
                }
                let candidate = try Interpreter(modelPath: modelPath, options: options, delegates: [ane])
                try candidate.allocateTensors()
                try warmup(candidate, maxSeqLen: maxSeqLen)
                interpreter = candidate
                log.info("Core ML ANE delegate active for \(modelName).tflite")
            } catch {
                log.info("Core ML delegate unusable (\(error.localizedDescription)); falling back to CPU")
                interpreter = try buildCPUInterpreter(modelPath: modelPath, options: options)
            }
        }
        guard let vocabPath = bundle.path(forResource: vocabName, ofType: "txt"),
              let vocabText = try? String(contentsOfFile: vocabPath, encoding: .utf8)
        else {
            throw LiteRTEmbedderError.missingVocab("\(vocabName).txt")
        }
        let vocab = vocabText.components(separatedBy: .newlines).filter { !$0.isEmpty }
        return LiteRTEmbedder(
            runner: InterpreterRunner(interpreter: interpreter),
            tokenizer: WordpieceTokenizer(vocab: vocab),
            maxSeqLen: maxSeqLen
        )
    }

    private static func buildCPUInterpreter(modelPath: String, options: Interpreter.Options) throws -> Interpreter {
        let interpreter = try Interpreter(modelPath: modelPath, options: options)
        try interpreter.allocateTensors()
        return interpreter
    }

    /// One throwaway inference proving the interpreter (and delegate) execute
    /// this graph. Content is meaningless; only success matters.
    private static func warmup(_ interpreter: Interpreter, maxSeqLen: Int) throws {
        var ids = [Int32](repeating: 0, count: maxSeqLen)
        var mask = [Int32](repeating: 0, count: maxSeqLen)
        let segment = [Int32](repeating: 0, count: maxSeqLen)
        ids[0] = 101
        ids[1] = 102
        mask[0] = 1
        mask[1] = 1
        let order = try InterpreterRunner.resolveInputOrder(names: (0..<interpreter.inputTensorCount).map {
            (try? interpreter.input(at: $0).name) ?? ""
        })
        try interpreter.copy(data(from: ids), toInputAt: order.ids)
        try interpreter.copy(data(from: mask), toInputAt: order.mask)
        try interpreter.copy(data(from: segment), toInputAt: order.segment)
        try interpreter.invoke()
    }
}
