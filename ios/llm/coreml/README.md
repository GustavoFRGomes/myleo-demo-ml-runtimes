# iOS LLM — Core ML

Two selectable runtimes (picker in the nav bar); **MiniCPM5-1B (Core ML) is the default**.

- **MiniCPM5-1B (Core ML)** — default. `Reza2kn/MiniCPM5-1B-CoreML-4bit` (Apache-2.0), 4-bit `.mlpackage` (~619 MB) + tokenizer, downloaded in-app. Fixed **128-token context**, stateless (`input_ids` Int32 `[1,128]` → `logits` Float16 `[1,128,130560]`), hand-written inference loop (tokenize → pad → predict → sample → slide window) with temperature/top-k/top-p. Runs on Core ML (ANE/GPU/CPU).
- **Apple Foundation Model** — system-hosted (`SystemLanguageModel`, iOS 26+), no download, temperature-only params, approximate token count.

- Scope contract (same as the other `*/llm/*` leafs): load + state/errors, streaming prompt→tokens with params, cancel/regenerate/clear, TTFT + tokens/sec, SwiftUI chat + settings + info FAB + Markdown/JSON transcript export.
- Model delivery: MiniCPM is fetched from Hugging Face into `Documents/Models/minicpm5-1b-coreml-4bit/` (`.mlpackage` compiled + cached on first load). Apple FM needs nothing.
- **Limitations (MiniCPM path, documented):** 128-token total context (prompt + reply share it — ~a sentence or two); no attention mask (prompt is left-aligned, pad id 1); token count is exact (sampled tokens). Not parity with the LiteRT/ONNX leaves' 4k–128k contexts.
- Build/run: `xcodegen generate`, then `open MyLeoLLMCoreML.xcodeproj` (SPM resolves `swift-transformers` on first build). Tests: `xcodebuild -scheme MyLeoLLMCoreML -destination 'platform=iOS Simulator,name=iPhone 17' test` — unit tests mock `ChatInference`, so no model is required.
- SDK note: `swift-transformers` (`Tokenizers` product) provides the BPE tokenizer/chat template; Core ML needs `MLModel.compileModel` at runtime for the downloaded package.

## Hand-wired MiniCPM setup (why this leaf is bespoke)

The Core ML path has no general chat runtime (unlike LiteRT-LM's `Engine` or ORT
GenAI's `Generator`), so `MiniCPMCoreMLEngine` hand-drives the `.mlpackage`:

- Package layout (`Reza2kn/MiniCPM5-1B-CoreML-4bit`): `MiniCPM5-1B-CoreML-4bit.mlpackage/Manifest.json` + `Data/com.apple.CoreML/model.mlmodel` + `Data/com.apple.CoreML/weights/weight.bin`, plus `tokenizer.json`, `tokenizer_config.json`, `chat_template.jinja`, `config.json`, `coreml_config.json` (downloaded for provenance; the engine only consumes the package + tokenizer files). Nested paths are preserved on download.
- Fixed shapes: stateless, 128-token context — `input_ids` Int32 `[1, 128]` → `logits` Float16 `[1, 128, 130560]` (vocab 130560). No KV cache.
- Load: `MLModel.compileModel(at:)` once (cached as `.mlmodelc`), then `MLModel(contentsOf:configuration:)` with `.cpuAndNeuralEngine`.
- Tokenizer: `swift-transformers` `AutoTokenizer.from(modelFolder:)` over the downloaded `tokenizer.json`.
- Loop: encode prompt → keep last 128 ids, left-align + pad id `1` → predict → read the Float16 logits row at the last real position (`logitsRow`, stride-aware) → temperature/top-k/top-p sample (`sample`, pure + unit-tested in `MiniCPMSamplerTests`) → stop on EOS `{1, 130073}` → decode + stream. Output is capped at 128 − prompt tokens.
- Prompt: hand-built ChatML (`<|im_start|>role\n…<|im_end|>\n` + trailing `<|im_start|>assistant\n`) matching MiniCPM's template.

## Switching this leaf to Phi-4-mini-instruct

Same-model parity with the other LLM leaves would mean replacing every MiniCPM-shaped
constant above — there is no drop-in Core ML export, so this leaf stays on MiniCPM + Apple FM:

- Model: no first-party Phi-4-mini Core ML package exists. Community ports (e.g. fp16 task-assistant fine-tunes) are a different checkpoint, different precision, and a different package/file layout — not the same model, and not wired to this engine's interface.
- Shapes/vocab: `contextLength` 128 → the export's context; logits vocab 130560 → 200064; pad id `1` → Phi-4's pad; EOS `{1, 130073}` → `{199999, 200020}` (`<|endoftext|>`, `<|end|>`); `logitsRow` must be re-derived for the new dtype/strides.
- Tokenizer + prompt: Phi-4's tiktoken-style BPE + `<|system|>/<|user|>/<|assistant|>/<|end|>` template (not ChatML) → `buildPrompt` changes; confirm `swift-transformers` supports the tokenizer first.
- Locator/downloader: folder + package name + nested file list + hint/size; compile-cache name; `ChatModelInfo` display/backend/source.
- Unchanged: `sample(...)` (model-agnostic), cancel/regenerate/clear, transcript export, Apple FM path.
- Tests: sampler tests carry over untouched; transcript/display-name fixtures and any shape constants update.
