# iOS LLM — Core ML / Foundation Models

- Runtime: Apple **Foundation Models** (system-hosted, on-device, Neural Engine/GPU) via the `FoundationModels` framework, iOS 26+
- Model format: system model (no file); ships with the OS. Fallback Core ML `.mlpackage` is not wired yet (see README note below)
- Scope contract (same as the other `*/llm/*` leafs):
  - Check availability; load session; show model id + state + errors
  - Stream tokens for a prompt (temperature; top-k / top-p / max-tokens are **not** supported by Foundation Models — documented gap)
  - Cancel / regenerate / clear, plus error and empty states
  - Report TTFT + tokens/sec (token count is approximated from whitespace — the framework exposes no token counter)
  - SwiftUI chat: system prompt field, streaming message thread, settings sheet
  - Info FAB (model id + source URL + load time + per-message timing) and Markdown/JSON transcript export
- Model: **Apple Foundation Model** (system-hosted, no download, no license/size to bundle) — availability depends on the device + Apple Intelligence being enabled. `SystemLanguageModel.default.availability` is checked at load; unavailable shows an actionable error.
- Parity notes vs the LiteRT-LM leaf:
  - No model download — `SystemModelLocator` stands in for `ModelLocator`, and the store is created with `downloadEnabled: false`.
  - `GenerationOptions` only exposes temperature; the other sliders are ignored (kept for UI parity).
  - Token metrics are approximate (`tok/s` derived from word count).
- Build/run: `xcodegen generate`, then `open MyLeoLLMCoreML.xcodeproj`. Tests: `xcodebuild -scheme MyLeoLLMCoreML -destination 'platform=iOS Simulator,name=iPhone 17' test` — unit tests mock `ChatInference`, so no model/device support is required.
- Fallback: a Core ML `.mlpackage` path (for iOS 17–25 / no Apple Intelligence) is planned but not implemented; document any model + conversion steps here when added.
