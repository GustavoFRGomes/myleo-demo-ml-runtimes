# iOS LLM — ONNX GenAI

- Runtime: ONNX Runtime GenAI `0.17.0` — prebuilt `onnxruntime-genai.xcframework` (C API) via a local CocoaPod spec + bridging header
- Model format: ORT GenAI package — `genai_config.json` + `model.onnx` (+ `.data`) + `tokenizer.json`/`tokenizer_config.json` + `chat_template.jinja`
- Scope contract (same as the other `*/llm/*` leafs):
  - Download model + load from a resolved path; show model id/state/errors
  - Stream tokens for a prompt with temperature / top-k / top-p / max-tokens
  - Cancel / regenerate / clear; error + empty states
  - Report TTFT + tokens/sec; per-message timing
  - SwiftUI chat + settings + info FAB + Markdown/JSON transcript export
- Model: **Phi-4-mini-instruct int4** (MIT) — https://huggingface.co/microsoft/Phi-4-mini-instruct-onnx, `cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/` (~4.9 GB). Same base model as the other LLM leaves.
- **Model parity note:** this is a *close copy, not a perfect match*, of the model the `litert-lm` leaf uses. Both derive from the same base checkpoint (Phi-4-mini-instruct) but use different precisions/quantizers (LiteRT q8 vs ONNX int4), so greedy outputs are **not byte-identical** and timings/sizes differ. See also `docs/benchmarks.md`.
- Delivery: first launch downloads 7 files from Hugging Face into `Documents/Models/phi-4-mini-instruct-onnx-int4/` (`OnnxGenAIFileSetDownloader`, aggregate byte progress, saved flat by basename). No manual step required.
- SDK note: onnxruntime-genai is **not on CocoaPods trunk** — it ships release pod-archives. This leaf uses `podspec/onnxruntime-genai-c.podspec` (source = the v0.17.0 release zip; the framework statically includes ONNX Runtime, so no separate ORT pod).
- Build/run: `xcodegen generate`, then `pod install`, then `open MyLeoLLMOnnxGenAI.xcworkspace` (always the workspace). Tests: `xcodebuild -workspace MyLeoLLMOnnxGenAI.xcworkspace -scheme MyLeoLLMOnnxGenAI -destination 'platform=iOS Simulator,name=iPhone 17' test` — unit tests mock `ChatInference`, so no model is required.
- Device note: a 3.8B int4 model needs ~4.5 GB RAM; run on a physical device for realistic TTFT / tokens-per-sec.
