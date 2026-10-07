import SwiftUI

/// Settings sheet: system prompt + generation parameters (F-LLM-2, F-LLM-5).
struct SettingsSheet: View {
    @ObservedObject var store: ChatStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("System prompt") {
                    TextEditor(text: Binding(
                        get: { store.state.systemPrompt },
                        set: { store.send(.systemPromptChanged($0)) }
                    ))
                    .frame(minHeight: 80)
                }
                Section("Generation") {
                    slider("Temperature", value: store.state.params.temperature, range: 0...2) { value in
                        var params = store.state.params
                        params.temperature = value
                        store.send(.paramsChanged(params))
                    }
                    slider("Top-p", value: store.state.params.topP, range: 0.05...1) { value in
                        var params = store.state.params
                        params.topP = value
                        store.send(.paramsChanged(params))
                    }
                    stepper("Top-k", value: store.state.params.topK, range: 1...100, step: 5) { value in
                        var params = store.state.params
                        params.topK = value
                        store.send(.paramsChanged(params))
                    }
                    stepper("Max tokens", value: store.state.params.maxTokens, range: 64...4096, step: 64) { value in
                        var params = store.state.params
                        params.maxTokens = value
                        store.send(.paramsChanged(params))
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func slider(
        _ title: String,
        value: Double,
        range: ClosedRange<Double>,
        onChange: @escaping (Double) -> Void
    ) -> some View {
        VStack(alignment: .leading) {
            HStack {
                Text(title)
                Spacer()
                Text(String(format: "%.2f", value)).foregroundStyle(.secondary)
            }
            Slider(value: Binding(get: { value }, set: onChange), in: range)
        }
    }

    private func stepper(
        _ title: String,
        value: Int,
        range: ClosedRange<Int>,
        step: Int,
        onChange: @escaping (Int) -> Void
    ) -> some View {
        Stepper("\(title): \(value)", value: Binding(get: { value }, set: onChange), in: range, step: step)
    }
}
