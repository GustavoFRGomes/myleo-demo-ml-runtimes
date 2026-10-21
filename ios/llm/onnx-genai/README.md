# iOS LLM — ONNX GenAI

- Runtime: ONNX Runtime GenAI `0.17.0` — prebuilt `onnxruntime-genai.xcframework` (C API) via a local CocoaPod spec + bridging header
- Model format: ORT GenAI package — `genai_config.json` + `model.onnx` (+ `.data`) + `tokenizer.json`/`tokenizer_config.json` + `chat_template.jinja`
- Scope contract (same as the other `*/llm/*` leafs):
  - Download model + load from a resolved path; show model id/state/errors
  - Stream tokens for a prompt with temperature / top-k / top-p / max-tokens
  - Cancel / regenerate / clear; error + empty states
  - Report TTFT + tokens/sec; per-message timing
  - SwiftUI chat + settings + info FAB + Markdown/JSON transcript export
- Model: **MiniCPM5-1B int4** (Apache-2.0) — https://huggingface.co/anydaytv/minicpm5-1b-onnx-int4, `model.onnx` + `model.onnx.data` + tokenizer (~1.43 GB). Same weights file as the Android `onnx-genai` leaf.
- **Package preparation:** the repo publishes no `genai_config.json` and its `tokenizer.json` contains `\p{N}+`, which ORT GenAI 0.17 rejects; `MiniCPM5GenAIConfig` writes the builder config and `TokenizerPatch` rewrites the regex on load (both idempotent, both unit-tested).
- **Prompt:** `MiniCPM5ChatTemplate` formats ChatML by hand (leading `<s>`, empty `<think>` block) — `OgaTokenizerApplyChatTemplate` corrupts the heap in 0.17.0.
- **Model parity note:** the same checkpoint as the `litert-lm` leaf, but different precisions/quantizers (LiteRT W4-block32 vs ONNX int4), so greedy outputs are **not byte-identical** and timings/sizes differ. Measured iOS numbers are in `docs/benchmarks.md`; toolchain and packaging caveats in `docs/ios-llm-hurdles.md`.
- Delivery: first launch downloads 7 files from Hugging Face into `Documents/Models/phi-4-mini-instruct-onnx-int4/` (`OnnxGenAIFileSetDownloader`, aggregate byte progress, saved flat by basename). No manual step required.
- SDK note: onnxruntime-genai is **not on CocoaPods trunk** — it ships release pod-archives. This leaf uses `podspec/onnxruntime-genai-c.podspec` (source = the v0.17.0 release zip; the framework statically includes ONNX Runtime, so no separate ORT pod).
- Build/run: `xcodegen generate`, then `pod install`, then `open MyLeoLLMOnnxGenAI.xcworkspace` (always the workspace). Tests: `xcodebuild -workspace MyLeoLLMOnnxGenAI.xcworkspace -scheme MyLeoLLMOnnxGenAI -destination 'platform=iOS Simulator,name=iPhone 17' test` — unit tests mock `ChatInference`, so no model is required.
- Device note: a 3.8B int4 model needs ~4.5 GB RAM; run on a physical device for realistic TTFT / tokens-per-sec.
