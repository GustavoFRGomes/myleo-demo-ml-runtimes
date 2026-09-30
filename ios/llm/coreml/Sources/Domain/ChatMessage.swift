import Foundation

/// Role of a message in the chat thread.
enum ChatRole: String, Equatable, Sendable {
    case system
    case user
    case assistant
}

/// One entry in the chat thread. `isStreaming` marks the assistant message
/// currently being generated (rendered with a live indicator). `metrics` is
/// filled in when an assistant turn finishes (F-LLM-4).
struct ChatMessage: Identifiable, Equatable, Sendable {
    let id: UUID
    var role: ChatRole
    var text: String
    var isStreaming: Bool
    var metrics: GenerationMetrics?

    init(
        id: UUID = UUID(),
        role: ChatRole,
        text: String,
        isStreaming: Bool = false,
        metrics: GenerationMetrics? = nil
    ) {
        self.id = id
        self.role = role
        self.text = text
        self.isStreaming = isStreaming
        self.metrics = metrics
    }

    /// Chat history handed to the runtime: system is passed separately, so
    /// only user/assistant turns are included.
    var isConversationTurn: Bool {
        role == .user || role == .assistant
    }
}
