import Foundation

/// Aggregated performance across all generations in a transcript.
struct TranscriptPerformance: Equatable, Sendable {
    let generations: Int
    let totalTokens: Int
    let totalDurationMs: Int64
    let averageTTFTMs: Double
    let averageTokensPerSecond: Double
    let fastestTokensPerSecond: Double
    let slowestTokensPerSecond: Double

    static let empty = TranscriptPerformance(
        generations: 0, totalTokens: 0, totalDurationMs: 0,
        averageTTFTMs: 0, averageTokensPerSecond: 0,
        fastestTokensPerSecond: 0, slowestTokensPerSecond: 0
    )

    /// Summarizes the assistant messages that have metrics. Pure — unit-tested.
    static func from(_ messages: [ChatMessage]) -> TranscriptPerformance {
        let metrics = messages.compactMap { $0.role == .assistant ? $0.metrics : nil }
        guard !metrics.isEmpty else { return .empty }
        let speeds = metrics.map(\.tokensPerSecond)
        let count = Double(metrics.count)
        return TranscriptPerformance(
            generations: metrics.count,
            totalTokens: metrics.reduce(0) { $0 + $1.generatedTokens },
            totalDurationMs: metrics.reduce(0) { $0 + $1.totalMs },
            averageTTFTMs: metrics.reduce(0.0) { $0 + Double($1.ttftMs) } / count,
            averageTokensPerSecond: speeds.reduce(0, +) / count,
            fastestTokensPerSecond: speeds.max() ?? 0,
            slowestTokensPerSecond: speeds.min() ?? 0
        )
    }
}

/// Everything a transcript export carries.
struct TranscriptData: Equatable, Sendable {
    let modelName: String?
    let modelBackend: String?
    let modelSizeBytes: Int64?
    let modelLoadMs: Int64?
    let sourceURL: String?
    let systemPrompt: String
    let params: GenerationParams
    let messages: [ChatMessage]

    var performance: TranscriptPerformance { .from(messages) }

    /// Builds from live screen state.
    init(state: ChatState) {
        let info = state.status.info
        self.modelName = info?.displayName
        self.modelBackend = info?.backend
        self.modelSizeBytes = info?.sizeBytes
        self.modelLoadMs = info?.loadMs
        self.sourceURL = info?.sourceURL
        self.systemPrompt = state.systemPrompt
        self.params = state.params
        self.messages = state.messages
    }

    init(
        modelName: String?, modelBackend: String?, modelSizeBytes: Int64?,
        modelLoadMs: Int64?, sourceURL: String?, systemPrompt: String,
        params: GenerationParams, messages: [ChatMessage]
    ) {
        self.modelName = modelName
        self.modelBackend = modelBackend
        self.modelSizeBytes = modelSizeBytes
        self.modelLoadMs = modelLoadMs
        self.sourceURL = sourceURL
        self.systemPrompt = systemPrompt
        self.params = params
        self.messages = messages
    }
}

func formatBytes(_ bytes: Int64) -> String {
    ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
}

/// Renders a transcript as Markdown. Pure function — unit-tested.
func buildTranscriptMarkdown(appLabel: String, data: TranscriptData, generatedAt: String) -> String {
    var lines: [String] = []
    lines.append("# \(appLabel) — Chat Transcript")
    lines.append("")
    lines.append("_Generated: \(generatedAt)_")
    lines.append("")
    lines.append("## Model")
    lines.append("")
    lines.append("- File: \(data.modelName ?? "not loaded")")
    lines.append("- Backend: \(data.modelBackend ?? "n/a")")
    lines.append("- Size: \(data.modelSizeBytes.map(formatBytes) ?? "n/a")")
    lines.append("- Source: \(data.sourceURL ?? "n/a")")
    lines.append("- Loaded in: \(data.modelLoadMs.map { "\($0) ms" } ?? "n/a")")
    lines.append("")
    lines.append("## Generation parameters")
    lines.append("")
    lines.append("- Temperature: \(String(format: "%.2f", data.params.temperature))")
    lines.append("- Top-k: \(data.params.topK)")
    lines.append("- Top-p: \(String(format: "%.2f", data.params.topP))")
    lines.append("- Max tokens: \(data.params.maxTokens)")
    lines.append("- System prompt: \(data.systemPrompt)")
    lines.append("")
    lines.append("## Performance")
    lines.append("")
    let perf = data.performance
    if perf.generations == 0 {
        lines.append("- no generations yet")
    } else {
        lines.append("- Generations: \(perf.generations)")
        lines.append("- Generated tokens: \(perf.totalTokens)")
        lines.append("- Total generation time: \(perf.totalDurationMs) ms")
        lines.append("- Average TTFT: \(String(format: "%.0f", perf.averageTTFTMs)) ms")
        lines.append("- Average speed: \(String(format: "%.1f", perf.averageTokensPerSecond)) tok/s")
        lines.append("- Fastest / slowest: \(String(format: "%.1f", perf.fastestTokensPerSecond)) / \(String(format: "%.1f", perf.slowestTokensPerSecond)) tok/s")
    }
    lines.append("")
    lines.append("## Conversation")
    lines.append("")
    if data.messages.isEmpty {
        lines.append("_empty_")
    }
    for (index, message) in data.messages.enumerated() {
        let title = message.role == .user ? "User" : "Assistant"
        lines.append("### \(index + 1). \(title)")
        lines.append("")
        lines.append(message.text.isEmpty ? "_(no content)_" : message.text)
        if let metrics = message.metrics {
            lines.append("")
            lines.append("- TTFT: \(metrics.ttftMs) ms")
            lines.append("- Duration: \(metrics.totalMs) ms")
            lines.append("- Tokens: \(metrics.generatedTokens)")
            lines.append("- Speed: \(String(format: "%.1f", metrics.tokensPerSecond)) tok/s")
        }
        lines.append("")
    }
    return lines.joined(separator: "\n")
}

/// Renders a transcript as JSON. Pure function — unit-tested.
func buildTranscriptJson(appLabel: String, data: TranscriptData, generatedAt: String) -> String {
    let perf = data.performance
    var lines: [String] = []
    lines.append("{")
    lines.append("  \"app\": \(json(appLabel)),")
    lines.append("  \"generatedAt\": \(json(generatedAt)),")
    lines.append("  \"model\": {\"name\": \(jsonOrNull(data.modelName)), \"backend\": \(jsonOrNull(data.modelBackend)), \"sizeBytes\": \(data.modelSizeBytes.map(String.init) ?? "null"), \"sourceURL\": \(jsonOrNull(data.sourceURL)), \"loadMs\": \(data.modelLoadMs.map(String.init) ?? "null")},")
    lines.append("  \"params\": {\"temperature\": \(data.params.temperature), \"topK\": \(data.params.topK), \"topP\": \(data.params.topP), \"maxTokens\": \(data.params.maxTokens), \"systemPrompt\": \(json(data.systemPrompt))},")
    lines.append("  \"performance\": {\"generations\": \(perf.generations), \"totalTokens\": \(perf.totalTokens), \"totalDurationMs\": \(perf.totalDurationMs), \"averageTTFTMs\": \(perf.averageTTFTMs), \"averageTokensPerSecond\": \(perf.averageTokensPerSecond), \"fastestTokensPerSecond\": \(perf.fastestTokensPerSecond), \"slowestTokensPerSecond\": \(perf.slowestTokensPerSecond)},")
    let messages = data.messages.map { message -> String in
        var fields = ["\"role\": \(json(message.role.rawValue))", "\"text\": \(json(message.text))"]
        if let metrics = message.metrics {
            fields.append("\"metrics\": {\"ttftMs\": \(metrics.ttftMs), \"totalMs\": \(metrics.totalMs), \"generatedTokens\": \(metrics.generatedTokens), \"tokensPerSecond\": \(metrics.tokensPerSecond)}")
        }
        return "{\(fields.joined(separator: ", "))}"
    }
    lines.append("  \"messages\": [\(messages.joined(separator: ", "))]")
    lines.append("}")
    return lines.joined(separator: "\n")
}

private func json(_ value: String) -> String { "\"\(escapeJson(value))\"" }

private func jsonOrNull(_ value: String?) -> String { value.map(json) ?? "null" }

internal func escapeJson(_ value: String) -> String {
    var result = ""
    for character in value {
        switch character {
        case "\"": result += "\\\""
        case "\\": result += "\\\\"
        case "\n": result += "\\n"
        case "\r": result += "\\r"
        case "\t": result += "\\t"
        default:
            if let scalar = character.unicodeScalars.first, scalar.value < 0x20 {
                result += String(format: "\\u%04x", scalar.value)
            } else {
                result.append(character)
            }
        }
    }
    return result
}
