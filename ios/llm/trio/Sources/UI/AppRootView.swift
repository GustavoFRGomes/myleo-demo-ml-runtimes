import SwiftUI

/// Switches between the runtime picker and the chat screen for the selected
/// runtime. Each runtime keeps its own `ChatStore` (thread + model state).
struct AppRootView: View {
    @ObservedObject var session: RuntimeSession

    var body: some View {
        if let runtime = session.current, let store = session.currentStore {
            ChatScreen(
                store: store,
                runtime: runtime,
                onSelectRuntime: { session.select($0) },
                onBack: { session.clearSelection() }
            )
            .id(runtime)
        } else {
            RuntimePickerScreen(session: session)
        }
    }
}
