import Foundation

/// Embedding/chat runtime available in the Core ML leaf.
enum ChatRuntime: String, CaseIterable, Identifiable, Sendable {
    /// MiniCPM5-1B 4-bit, exported to Core ML (downloaded). Default.
    case miniCPM
    /// Apple's system-hosted Foundation Model (no download; iOS 26+).
    case appleFoundationModel

    var id: String { rawValue }

    var label: String {
        switch self {
        case .miniCPM: "MiniCPM5-1B (Core ML)"
        case .appleFoundationModel: "Apple Foundation Model"
        }
    }

    var subtitle: String {
        switch self {
        case .miniCPM: "4-bit Core ML, 128-token context, ~619 MB download"
        case .appleFoundationModel: "System-hosted, no download, iOS 26+"
        }
    }

    var downloadsModel: Bool {
        self == .miniCPM
    }
}
