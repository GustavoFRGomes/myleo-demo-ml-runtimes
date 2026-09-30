# iOS LLM — Core ML

Two selectable runtimes (picker in the nav bar); **MiniCPM5-1B (Core ML) is the default**.

- **MiniCPM5-1B (Core ML)** — default. `Reza2kn/MiniCPM5-1B-CoreML-4bit` (Apache-2.0), 4-bit `.mlpackage` (~619 MB) + tokenizer, downloaded in-app. Fixed **128-token context**, stateless (`input_ids` Int32 `[1,128]` → `logits` Float16 `[1,128,130560]`), hand-written inference loop (tokenize → pad → predict → sample → slide window) with temperature/top-k/top-p. Runs on Core ML (ANE/GPU/CPU).
- **Apple Foundation Model** — system-hosted (`SystemLanguageModel`, iOS 26+), no download, temperature-only params, approximate token count.

- Scope contract (same as the other `*/llm/*` leafs): load + state/errors, streaming prompt→tokens with params, cancel/regenerate/clear, TTFT + tokens/sec, SwiftUI chat + settings + info FAB + Markdown/JSON transcript export.
- Model delivery: MiniCPM is fetched from Hugging Face into `Documents/Models/minicpm5-1b-coreml-4bit/` (`.mlpackage` compiled + cached on first load). Apple FM needs nothing.
- **Limitations (MiniCPM path, documented):** 128-token total context (prompt + reply share it — ~a sentence or two); no attention mask (prompt is left-aligned, pad id 1); token count is exact (sampled tokens). Not parity with the LiteRT/ONNX leaves' 4k–128k contexts.
- Build/run: `xcodegen generate`, then `open MyLeoLLMCoreML.xcodeproj` (SPM resolves `swift-transformers` on first build). Tests: `xcodebuild -scheme MyLeoLLMCoreML -destination 'platform=iOS Simulator,name=iPhone 17' test` — unit tests mock `ChatInference`, so no model is required.
- SDK note: `swift-transformers` (`Tokenizers` product) provides the BPE tokenizer/chat template; Core ML needs `MLModel.compileModel` at runtime for the downloaded package.
