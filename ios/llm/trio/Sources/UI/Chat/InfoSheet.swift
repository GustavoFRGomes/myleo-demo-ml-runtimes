import SwiftUI

/// Info sheet (mirrors the vector-search statistics sheet): model identity,
/// size, source URL and load time, plus a per-message log of every generation
/// with its result and timing.
struct InfoSheet: View {
    let state: ChatState
    var appLabel: String = "MyLeo LLM Trio"
    @Environment(\.dismiss) private var dismiss
    @State private var shareItem: ShareItem?
    @State private var exportError: String?

    var body: some View {
        NavigationStack {
            List {
                modelSection
                generationsSection
                exportSection
            }
            .navigationTitle("Info")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(item: $shareItem) { item in
                ShareSheet(url: item.url)
            }
        }
    }

    private var exportSection: some View {
        Section("Export") {
            HStack(spacing: 8) {
                Button("Export .md") { export(.markdown) }
                    .buttonStyle(.borderedProminent)
                Button("Export JSON") { export(.json) }
                    .buttonStyle(.bordered)
            }
            Text("Includes model info, parameters, every prompt + response, and per-message timing.")
                .font(.caption2)
                .foregroundStyle(.secondary)
            if let exportError {
                Text(exportError).font(.caption).foregroundStyle(.red)
            }
        }
    }

    private func export(_ format: TranscriptFormat) {
        let generatedAt = ISO8601DateFormatter().string(from: Date())
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMdd-HHmmss"
        let fileName = "myleo-chat-\(formatter.string(from: Date())).\(format.fileExtension)"
        let content = format.render(
            appLabel: appLabel,
            data: TranscriptData(state: state),
            generatedAt: generatedAt
        )
        let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        do {
            let url = try TranscriptExporter(directory: directory)
                .export(fileName: fileName, content: content)
            exportError = nil
            shareItem = ShareItem(url: url)
        } catch {
            exportError = "export failed: \(error.localizedDescription)"
        }
    }

    private var modelSection: some View {
        Section("Model") {
            if let info = state.status.info {
                LabeledContent("File", value: info.displayName)
                LabeledContent("Size", value: Self.formatBytes(info.sizeBytes))
                LabeledContent("Backend", value: info.backend)
                LabeledContent("Load time", value: "\(info.loadMs) ms")
                VStack(alignment: .leading, spacing: 2) {
                    Text("Source").font(.caption).foregroundStyle(.secondary)
                    Text(info.sourceURL ?? ModelLocator.remoteURL(for: info.displayName).absoluteString)
                        .font(.caption2)
                        .textSelection(.enabled)
                }
            } else {
                Text("Model not loaded").foregroundStyle(.secondary)
            }
            if case .failed(let message) = state.download {
                Text("Last download: \(message)").font(.caption).foregroundStyle(.red)
            }
        }
    }

    private var generationsSection: some View {
        Section("Generations") {
            let assistants = state.messages.filter { $0.role == .assistant }
            if assistants.isEmpty {
                Text("No generations yet").foregroundStyle(.secondary)
            }
            ForEach(assistants) { message in
                VStack(alignment: .leading, spacing: 4) {
                    Text(message.text.isEmpty ? "(streaming…)" : message.text)
                        .font(.callout)
                        .lineLimit(4)
                    if let metrics = message.metrics {
                        Text(String(
                            format: "TTFT %.0f ms · total %.0f ms · %.1f tok/s · %d tokens",
                            Double(metrics.ttftMs),
                            Double(metrics.totalMs),
                            metrics.tokensPerSecond,
                            metrics.generatedTokens
                        ))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    } else {
                        Text("no timing").font(.caption2).foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    static func formatBytes(_ bytes: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
    }
}

#Preview {
    InfoSheet(state: ChatState(
        status: .ready(ChatModelInfo(
            displayName: "minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm",
            backend: "GPU",
            loadMs: 1450,
            maxContextTokens: 0,
            sizeBytes: 831_000_000,
            sourceURL: nil
        )),
        messages: [
            ChatMessage(role: .user, text: "Hello"),
            ChatMessage(
                role: .assistant,
                text: "Hi there, how can I help?",
                metrics: GenerationMetrics(ttftMs: 320, totalMs: 1800, generatedTokens: 24)
            ),
        ]
    ))
}
