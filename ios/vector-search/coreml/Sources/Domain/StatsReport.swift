import Foundation

/// Last search summary for the report.
struct QueryStats: Equatable {
    let query: String
    let hits: Int
    let ms: Int64
}

/// Everything the statistics sheet/export shows.
struct StatsReportData: Equatable {
    let device: DeviceSnapshot
    let modelName: String?
    let modelUrl: String?
    let modelLoadMs: Int64?
    let modelDimensions: Int?
    let chunkSize: Int?
    let corpus: CorpusStats
    let documents: [DocumentStat]
    let lastRun: IngestRunStats?
    let lastQuery: QueryStats?
}

/// Renders `data` as a Markdown report. Pure function — unit-tested.
func buildStatsMarkdown(appLabel: String, data: StatsReportData, generatedAt: String) -> String {
    let power: String
    if let pct = data.device.batteryPercent {
        power = data.device.onBattery ? "on battery (\(pct)%)" : "charging (\(pct)%)"
    } else {
        power = "unknown"
    }
    var lines: [String] = []
    lines.append("# \(appLabel) — Vector Search Statistics")
    lines.append("")
    lines.append("_Generated: \(generatedAt)_")
    lines.append("")
    lines.append("## Embedding model")
    lines.append("")
    lines.append("- Model: \(data.modelName ?? "not bundled")")
    lines.append("- URL: \(data.modelUrl ?? "n/a")")
    lines.append("- Dimensions: \(data.modelDimensions.map(String.init) ?? "n/a")")
    lines.append("- Chunk size: \(data.chunkSize.map { "\($0) tokens" } ?? "n/a")")
    lines.append("- Loaded in: \(data.modelLoadMs.map { "\($0) ms" } ?? "n/a")")
    lines.append("")
    lines.append("## Device")
    lines.append("")
    lines.append("- \(data.device.manufacturer) \(data.device.model)")
    lines.append("- \(specsLine(data.device))")
    lines.append("- Power: \(power)")
    lines.append("")
    lines.append("## Index")
    lines.append("")
    lines.append("- Documents: \(data.corpus.documents), Chunks: \(data.corpus.chunks)")
    for doc in data.documents {
        lines.append("- \(doc.label): \(doc.chunks) chunks")
    }
    lines.append("")
    lines.append("## Last ingest run")
    lines.append("")
    if let run = data.lastRun {
        lines.append("- \(run.document): \(run.chunks) chunks in \(run.totalMs) ms (embed \(run.embedMs) ms, store \(run.storeMs) ms)")
        lines.append("- Battery: \(pct(run.batteryStartPct)) → \(pct(run.batteryEndPct))")
    } else {
        lines.append("- none yet")
    }
    lines.append("")
    lines.append("## Last search")
    lines.append("")
    if let query = data.lastQuery {
        lines.append("- \"\(query.query)\": \(query.hits) hits in \(query.ms) ms")
    } else {
        lines.append("- none yet")
    }
    return lines.joined(separator: "\n") + "\n"
}

/// Renders `data` as a JSON report. Pure function — unit-tested.
func buildStatsJson(appLabel: String, data: StatsReportData, generatedAt: String) -> String {
    let device = data.device
    var out: [String] = []
    out.append("{")
    out.append("  \"app\": \(json(appLabel)),")
    out.append("  \"generatedAt\": \(json(generatedAt)),")
    out.append("  \"model\": {\"name\": \(jsonOrNull(data.modelName)), \"url\": \(jsonOrNull(data.modelUrl)), \"loadMs\": \(data.modelLoadMs.map(String.init) ?? "null"), \"dimensions\": \(data.modelDimensions.map(String.init) ?? "null"), \"chunkSizeTokens\": \(data.chunkSize.map(String.init) ?? "null")},")
    out.append("  \"device\": {\"manufacturer\": \(json(device.manufacturer)), \"model\": \(json(device.model)), \"ramGB\": \(device.ramGB.map { String(format: "%.1f", $0) } ?? "null"), \"cpuCores\": \(device.cpuCores.map(String.init) ?? "null"), \"gpuCores\": \(device.gpuCores.map(String.init) ?? "null"), \"aneCores\": \(device.aneCores.map(String.init) ?? "null"), \"onBattery\": \(device.onBattery ? "true" : "false"), \"batteryPercent\": \(device.batteryPercent.map(String.init) ?? "null")},")
    let perDoc = data.documents.map { "{\"label\": \(json($0.label)), \"chunks\": \($0.chunks)}" }.joined(separator: ", ")
    out.append("  \"index\": {\"documents\": \(data.corpus.documents), \"chunks\": \(data.corpus.chunks), \"perDocument\": [\(perDoc)]},")
    if let run = data.lastRun {
        out.append("  \"lastIngestRun\": {\"document\": \(json(run.document)), \"chunks\": \(run.chunks), \"embedMs\": \(run.embedMs), \"storeMs\": \(run.storeMs), \"totalMs\": \(run.totalMs), \"batteryStartPct\": \(run.batteryStartPct.map(String.init) ?? "null"), \"batteryEndPct\": \(run.batteryEndPct.map(String.init) ?? "null")},")
    } else {
        out.append("  \"lastIngestRun\": null,")
    }
    if let query = data.lastQuery {
        out.append("  \"lastSearch\": {\"query\": \(json(query.query)), \"hits\": \(query.hits), \"ms\": \(query.ms)}")
    } else {
        out.append("  \"lastSearch\": null")
    }
    out.append("}")
    return out.joined(separator: "\n") + "\n"
}

private func pct(_ value: Int?) -> String {
    value.map { "\($0)%" } ?? "n/a"
}

private func specsLine(_ device: DeviceSnapshot) -> String {
    func text(_ value: CustomStringConvertible?) -> String {
        value.map(String.init(describing:)) ?? "unknown"
    }
    let ram = device.ramGB.map {
        String(format: "%.1f GB", locale: Locale(identifier: "en_US_POSIX"), $0)
    } ?? "unknown RAM"
    return "RAM: \(ram) · CPU: \(text(device.cpuCores)) · GPU: \(text(device.gpuCores)) · ANE: \(text(device.aneCores))"
}

private func json(_ value: String) -> String {
    "\"\(escapeJson(value))\""
}

private func jsonOrNull(_ value: String?) -> String {
    value.map(json) ?? "null"
}

func escapeJson(_ value: String) -> String {
    var out = ""
    out.reserveCapacity(value.count)
    for scalar in value.unicodeScalars {
        switch scalar.value {
        case 0x22: out += "\\\""
        case 0x5C: out += "\\\\"
        case 0x0A: out += "\\n"
        case 0x0D: out += "\\r"
        case 0x09: out += "\\t"
        default:
            if scalar.value < 0x20 {
                out += String(format: "\\u%04x", scalar.value)
            } else {
                out.unicodeScalars.append(scalar)
            }
        }
    }
    return out
}
