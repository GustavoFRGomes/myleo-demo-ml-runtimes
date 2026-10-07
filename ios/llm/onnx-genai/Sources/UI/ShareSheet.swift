import SwiftUI
import UIKit

/// Identifiable wrapper for the share sheet.
struct ShareItem: Identifiable {
    let id = UUID()
    let url: URL
}

/// System share sheet for an exported file.
struct ShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
