import Foundation

/// Model lifecycle for the chat screen (F-LLM-1).
enum ChatModelStatus: Equatable {
    case idle
    case loading
    case ready(ChatModelInfo)
    case failed(String)

    var isLoading: Bool {
        if case .loading = self { return true }
        return false
    }

    var info: ChatModelInfo? {
        if case .ready(let info) = self { return info }
        return nil
    }

    var isFailed: Bool {
        if case .failed = self { return true }
        return false
    }
}

/// Screen state (MVI). Rendered by the view, never mutated there.
struct ChatState: Equatable {
    var status: ChatModelStatus = .idle
    var download: ModelDownloadState = .idle
    var messages: [ChatMessage] = []
    var input: String = ""
    var systemPrompt: String = "You are a helpful assistant."
    var params: GenerationParams = .default
    var isGenerating: Bool = false
    var lastMetrics: GenerationMetrics?
    var error: String?
    /// Whether the runtime downloads its model (false for system-hosted models).
    var downloadEnabled: Bool = true

    var canSend: Bool {
        !input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !isGenerating
            && status.info != nil
    }

    var canRegenerate: Bool {
        !isGenerating && status.info != nil && messages.contains { $0.role == .user }
    }

    /// Model file absent — offer the in-app download instead of a dead end.
    var canDownload: Bool {
        downloadEnabled && status.isFailed && !download.isDownloading
    }
}

/// User intents (MVI). The view sends these, the store reduces them.
enum ChatIntent: Equatable {
    case load
    case retryLoad
    case downloadModel
    case cancelDownload
    case inputChanged(String)
    case systemPromptChanged(String)
    case paramsChanged(GenerationParams)
    case send
    case cancel
    case regenerate
    case clear
    case dismissError
}
