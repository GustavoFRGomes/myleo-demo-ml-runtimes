import SwiftUI

/// Chat demo UI (F-LLM-5): system prompt + params in a sheet, a streaming
/// message thread, and TTFT / tokens-per-sec reporting (F-LLM-4).
struct ChatScreen: View {
    @ObservedObject var store: ChatStore
    var runtime: InferenceRuntime? = nil
    var onSelectRuntime: ((InferenceRuntime) -> Void)? = nil
    var onBack: (() -> Void)? = nil
    @State private var showSettings = false
    @State private var showInfo = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                statusBanner
                messageList
                if let metrics = store.state.lastMetrics {
                    metricsBar(metrics)
                }
                if let error = store.state.error {
                    errorBar(error)
                }
                inputBar
            }
            .navigationTitle("MyLeo LLM Trio")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if let onBack {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button { onBack() } label: {
                            Label("Runtimes", systemImage: "chevron.backward")
                        }
                    }
                }
                ToolbarItem(placement: .principal) {
                    if let runtime {
                        Menu {
                            ForEach(InferenceRuntime.allCases) { option in
                                Button {
                                    if option != runtime { onSelectRuntime?(option) }
                                } label: {
                                    Label(option.label, systemImage: "cpu")
                                }
                            }
                        } label: {
                            Label(runtime.label, systemImage: "cpu")
                        }
                    } else {
                        Text("MyLeo LLM Trio")
                    }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { showSettings = true } label: {
                        Label("Settings", systemImage: "slider.horizontal.3")
                    }
                    .disabled(store.state.status.info == nil)
                }
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        Button { store.send(.regenerate) } label: {
                            Label("Regenerate", systemImage: "arrow.clockwise")
                        }
                        .disabled(!store.state.canRegenerate)
                        Button(role: .destructive) { store.send(.clear) } label: {
                            Label("Clear", systemImage: "trash")
                        }
                        .disabled(store.state.messages.isEmpty || store.state.isGenerating)
                    } label: {
                        Label("More", systemImage: "ellipsis.circle")
                    }
                }
            }
            .sheet(isPresented: $showSettings) {
                SettingsSheet(store: store)
            }
            .sheet(isPresented: $showInfo) {
                InfoSheet(state: store.state)
            }            .onAppear { store.send(.load) }
        }
    }

    @ViewBuilder
    private var statusBanner: some View {
        switch store.state.status {
        case .idle:
            EmptyView()
        case .loading:
            HStack(spacing: 8) {
                ProgressView()
                Text("Loading model…").font(.footnote).foregroundStyle(.secondary)
            }
            .padding(8)
        case .ready(let info):
            Text("\(info.displayName) · \(info.backend) · loaded in \(info.loadMs) ms")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding(8)
        case .failed(let message):
            VStack(alignment: .leading, spacing: 6) {
                Text(message).font(.footnote).foregroundStyle(.red)
                downloadControls
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(8)
        }
    }

    @ViewBuilder
    private var downloadControls: some View {
        switch store.state.download {
        case .downloading(let progress):
            ProgressView(value: progress.fraction) {
                Text(String(format: "Downloading %.0f / %.0f MB",
                            progress.receivedMB,
                            progress.totalMB))
                    .font(.caption)
            }
            Button("Cancel download") { store.send(.cancelDownload) }.font(.footnote)
        case .failed(let message):
            Text(message).font(.caption).foregroundStyle(.red)
            Button("Retry download") { store.send(.downloadModel) }.font(.footnote)
        default:
            Button("Download model") { store.send(.downloadModel) }
                .font(.footnote)
            Text("Fetches the model from Hugging Face on first use.")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 8) {
                    ForEach(store.state.messages) { message in
                        MessageBubble(message: message)
                            .id(message.id)
                    }
                }
                .padding()
            }
            .onChange(of: store.state.messages.last?.text) { _, _ in
                guard let last = store.state.messages.last else { return }
                withAnimation(.easeOut(duration: 0.1)) { proxy.scrollTo(last.id, anchor: .bottom) }
            }
            .overlay(alignment: .bottomTrailing) {
                // Info FAB — model + per-generation timing.
                Button { showInfo = true } label: {
                    Image(systemName: "info.circle.fill")
                        .font(.title)
                        .symbolRenderingMode(.hierarchical)
                        .background(Circle().fill(.background))
                }
                .padding(12)
                .accessibilityLabel("Info")
            }
        }
    }

    private func metricsBar(_ metrics: GenerationMetrics) -> some View {
        Text(String(format: "TTFT %.0f ms · %.1f tok/s · %d tokens",
                    Double(metrics.ttftMs), metrics.tokensPerSecond, metrics.generatedTokens))
            .font(.caption)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal)
    }

    private func errorBar(_ error: String) -> some View {
        HStack {
            Text(error).font(.footnote).foregroundStyle(.red)
            Spacer()
            Button("Dismiss") { store.send(.dismissError) }.font(.footnote)
        }
        .padding(.horizontal)
    }

    private var inputBar: some View {
        HStack(spacing: 8) {
            TextField("Message", text: Binding(
                get: { store.state.input },
                set: { store.send(.inputChanged($0)) }
            ), axis: .vertical)
            .textFieldStyle(.roundedBorder)
            .lineLimit(1...4)
            .disabled(store.state.status.info == nil)

            if store.state.isGenerating {
                Button(role: .destructive) { store.send(.cancel) } label: {
                    Image(systemName: "stop.circle.fill")
                }
                .font(.title2)
            } else {
                Button { store.send(.send) } label: {
                    Image(systemName: "arrow.up.circle.fill")
                }
                .accessibilityLabel("Send")
                .font(.title2)
                .disabled(!store.state.canSend)
            }
        }
        .padding(8)
    }
}

private struct MessageBubble: View {
    let message: ChatMessage

    var body: some View {
        HStack {
            if message.role == .user { Spacer(minLength: 40) }
            VStack(alignment: .leading, spacing: 4) {
                Text(message.role == .user ? "You" : "Assistant")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                if message.text.isEmpty && message.isStreaming {
                    ProgressView()
                } else {
                    Text(message.text)
                }
                if let metrics = message.metrics {
                    Text(String(
                        format: "%.0f ms · %.1f tok/s",
                        Double(metrics.totalMs),
                        metrics.tokensPerSecond
                    ))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                }
            }
            .padding(10)
            .background(message.role == .user ? Color.accentColor.opacity(0.15) : Color.gray.opacity(0.15))
            .clipShape(RoundedRectangle(cornerRadius: 10))
            if message.role != .user { Spacer(minLength: 40) }
        }
    }
}

#Preview {
    ChatScreen(store: ChatStore(
        locate: { URL(fileURLWithPath: "/tmp/preview.litertlm") },
        makeInference: { _ in PreviewChatInference() },
        downloader: PreviewModelDownloader()
    ))
}
