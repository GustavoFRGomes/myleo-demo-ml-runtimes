import Foundation

struct OnnxRunResult: Sendable {
    let logits: [Float]
    let writeMs: Double
    let runMs: Double
    let readMs: Double
}

/// ONNX Runtime **C API** runner for Julia-1.
///
/// The ObjC wrapper (`ORTSession`) has no bool element type, and this graph's
/// `marker_mask` input is `tensor(bool)`, so the C API is the only faithful surface. All five
/// inputs are created per call and released afterwards; the session, environment and CPU memory
/// info live for the runner's lifetime.
final class OnnxCGraphRunner: @unchecked Sendable {
    private let api: UnsafePointer<OrtApi>
    private let env: OpaquePointer
    private let session: OpaquePointer
    private let memory: OpaquePointer

    private static let inputNames = ["input_ids", "attention_mask", "marker_pos", "marker_mask", "qtype"]
    private static let outputName = "logits"

    init(modelPath: String, threads: Int32 = 4) throws {
        guard let base = OrtGetApiBase(), let api = base.pointee.GetApi(UInt32(ORT_API_VERSION)) else {
            throw DecisionError.model("OrtGetApiBase returned no API")
        }
        self.api = api

        var env: OpaquePointer?
        try Self.check(api.pointee.CreateEnv(ORT_LOGGING_LEVEL_WARNING, "myleo-decision", &env), api)
        guard let env else { throw DecisionError.model("CreateEnv returned nil") }
        self.env = env

        var options: OpaquePointer?
        try Self.check(api.pointee.CreateSessionOptions(&options), api)
        defer { api.pointee.ReleaseSessionOptions(options) }
        try Self.check(api.pointee.SetIntraOpNumThreads(options, threads), api)
        try Self.check(api.pointee.SetSessionGraphOptimizationLevel(options, ORT_ENABLE_ALL), api)

        var session: OpaquePointer?
        try Self.check(api.pointee.CreateSession(env, modelPath, options, &session), api)
        guard let session else { throw DecisionError.model("CreateSession returned nil") }
        self.session = session

        var memory: OpaquePointer?
        try Self.check(api.pointee.CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &memory), api)
        guard let memory else { throw DecisionError.model("CreateCpuMemoryInfo returned nil") }
        self.memory = memory
    }

    deinit {
        api.pointee.ReleaseSession(session)
        api.pointee.ReleaseMemoryInfo(memory)
        api.pointee.ReleaseEnv(env)
    }

    /// `ids`/`mask` are padded to a multiple of 8 (the author's host convention); marker
    /// positions stay absolute because padding is appended, never inserted.
    func run(ids: [Int], mask: [Int], markers: [Int], qtype: Int) throws -> OnnxRunResult {
        precondition(ids.count == mask.count, "ids/mask length mismatch")
        let tokens = (ids.count + 7) / 8 * 8
        var paddedIds = ids
        var paddedMask = mask
        paddedIds.append(contentsOf: repeatElement(0, count: tokens - ids.count))
        paddedMask.append(contentsOf: repeatElement(0, count: tokens - mask.count))

        let idsData = paddedIds.map(Int64.init)
        let maskData = paddedMask.map(Int64.init)
        let markerData = markers.map(Int64.init)
        let markerMask = [UInt8](repeating: 1, count: markers.count)
        let qtypeData: [Int64] = [Int64(qtype)]

        let writeStart = DispatchTime.now().uptimeNanoseconds
        let idsTensor = try tensor(idsData, type: ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64, shape: [1, Int64(tokens)])
        let maskTensor = try tensor(maskData, type: ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64, shape: [1, Int64(tokens)])
        let markerTensor = try tensor(markerData, type: ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64, shape: [1, Int64(markers.count)])
        let markerMaskTensor = try tensor(markerMask, type: ONNX_TENSOR_ELEMENT_DATA_TYPE_BOOL, shape: [1, Int64(markers.count)])
        let qtypeTensor = try tensor(qtypeData, type: ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64, shape: [1])
        let written = DispatchTime.now().uptimeNanoseconds

        var output: OpaquePointer?
        try runSession(
            values: [idsTensor.0, maskTensor.0, markerTensor.0, markerMaskTensor.0, qtypeTensor.0],
            output: &output
        )
        let ran = DispatchTime.now().uptimeNanoseconds
        guard let output else { throw DecisionError.graph("Run returned no logits") }
        defer { api.pointee.ReleaseValue(output) }

        var raw: UnsafeMutableRawPointer?
        try Self.check(api.pointee.GetTensorMutableData(output, &raw), api)
        guard let raw else { throw DecisionError.graph("logits tensor has no data") }
        let count = markers.count
        let logits = Array(UnsafeBufferPointer(start: raw.assumingMemoryBound(to: Float.self), count: count))
        let read = DispatchTime.now().uptimeNanoseconds

        // Inputs may be released once Run returns.
        for value in [idsTensor.0, maskTensor.0, markerTensor.0, markerMaskTensor.0, qtypeTensor.0] {
            api.pointee.ReleaseValue(value)
        }

        return OnnxRunResult(
            logits: logits,
            writeMs: Self.ms(written - writeStart),
            runMs: Self.ms(ran - written),
            readMs: Self.ms(read - ran)
        )
    }

    // MARK: - Helpers

    private func tensor<T>(_ values: [T], type: ONNXTensorElementDataType, shape: [Int64]) throws -> (OpaquePointer, NSMutableData) {
        let data = values.withUnsafeBytes { NSMutableData(bytes: $0.baseAddress!, length: $0.count) }
        var value: OpaquePointer?
        try shape.withUnsafeBufferPointer { buffer in
            try Self.check(
                api.pointee.CreateTensorWithDataAsOrtValue(
                    memory, data.mutableBytes, data.length, buffer.baseAddress, buffer.count, type, &value
                ),
                api
            )
        }
        guard let value else { throw DecisionError.graph("CreateTensorWithDataAsOrtValue returned nil") }
        return (value, data)
    }

    private func runSession(values: [OpaquePointer], output: inout OpaquePointer?) throws {
        let nameCStrings = Self.inputNames.map { strdup($0)! }
        let outputCString = strdup(Self.outputName)!
        defer {
            nameCStrings.forEach { free($0) }
            free(outputCString)
        }
        let names: [UnsafePointer<CChar>?] = nameCStrings.map { UnsafePointer($0) }
        let outputs: [UnsafePointer<CChar>?] = [UnsafePointer(outputCString)]
        let valuesOptional: [OpaquePointer?] = values.map { $0 }

        try names.withUnsafeBufferPointer { nameBuffer in
            try valuesOptional.withUnsafeBufferPointer { valueBuffer in
                try outputs.withUnsafeBufferPointer { outputBuffer in
                    try Self.check(
                        api.pointee.Run(
                            session, nil,
                            nameBuffer.baseAddress, valueBuffer.baseAddress, values.count,
                            outputBuffer.baseAddress, 1, &output
                        ),
                        api
                    )
                }
            }
        }
    }

    private static func check(_ status: OpaquePointer?, _ api: UnsafePointer<OrtApi>) throws {
        guard let status else { return }
        let message = String(cString: api.pointee.GetErrorMessage(status)!)
        api.pointee.ReleaseStatus(status)
        throw DecisionError.graph(message)
    }

    private static func ms(_ nanos: UInt64) -> Double { Double(nanos) / 1_000_000 }
}
