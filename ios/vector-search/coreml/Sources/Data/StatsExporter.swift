import Foundation

/// Export formats offered by the stats sheet.
enum StatsFormat {
    case markdown
    case json

    var fileExtension: String {
        switch self {
        case .markdown: "md"
        case .json: "json"
        }
    }

    var mimeType: String {
        switch self {
        case .markdown: "text/markdown"
        case .json: "application/json"
        }
    }

    func render(appLabel: String, data: StatsReportData, generatedAt: String) -> String {
        switch self {
        case .markdown: buildStatsMarkdown(appLabel: appLabel, data: data, generatedAt: generatedAt)
        case .json: buildStatsJson(appLabel: appLabel, data: data, generatedAt: generatedAt)
        }
    }
}

/// Writes report files. Directory-injectable so unit tests can use a temp dir.
struct StatsExporter {
    let directory: URL

    func export(fileName: String, content: String) throws -> URL {
        let url = directory.appendingPathComponent(fileName)
        try content.write(to: url, atomically: true, encoding: .utf8)
        return url
    }
}
