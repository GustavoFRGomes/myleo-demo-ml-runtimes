# iOS LLM — ONNX GenAI

- Runtime: ONNX Runtime GenAI `0.17.0` — prebuilt `onnxruntime-genai.xcframework` (C API) via a local CocoaPod spec + bridging header
- Model format: ORT GenAI package — `genai_config.json` + `model.onnx` (+ `.data`) + `tokenizer.json`/`tokenizer_config.json` + `chat_template.jinja`
- Scope contract (same as the other `*/llm/*` leafs):
  - Download model + load from a resolved path; show model id/state/errors
  - Stream tokens for a prompt with temperature / top-k / top-p / max-tokens
  - Cancel / regenerate / clear; error + empty states
  - Report TTFT + tokens/sec; per-message timing
  - SwiftUI chat + settings + info FAB + Markdown/JSON transcript export
- Model: **MiniCPM5-1B int4** (Apache-2.0) — weights from https://huggingface.co/anydaytv/minicpm5-1b-onnx-int4 (**int4** MatMulNBits, block 32, fp32 activations, ~1.42 GB), config from https://huggingface.co/onnx-community/MiniCPM5-1B (`genai_config.json` + `chat_template.jinja` — the int4 repo omits these).
- **Model parity note:** this is a *close copy, not a perfect match*, of the model the `litert-lm` leaf uses. Both derive from the same base checkpoint (`openbmb/MiniCPM5-1B`) and are both ~4-bit weight quantized with block size 32, but the quantizers differ (LiteRT's W4 scheme vs ONNX MatMulNBits), so greedy outputs are **not byte-identical** and timings/sizes differ (ONNX int4 ~1.42 GB vs LiteRT W4 ~793 MB — ONNX keeps more tensors at higher precision). See also `docs/benchmarks.md`.
- Delivery: first launch downloads 6 files from Hugging Face into `Documents/Models/minicpm5-1b-onnx-int4/` (`OnnxGenAIFileSetDownloader`, aggregate byte progress). No manual step required.
- SDK note: onnxruntime-genai is **not on CocoaPods trunk** — it ships release pod-archives. This leaf uses `podspec/onnxruntime-genai-c.podspec` (source = the v0.17.0 release zip; the framework statically includes ONNX Runtime, so no separate ORT pod).
- Build/run: `xcodegen generate`, then `pod install`, then `open MyLeoLLMOnnxGenAI.xcworkspace` (always the workspace). Tests: `xcodebuild -workspace MyLeoLLMOnnxGenAI.xcworkspace -scheme MyLeoLLMOnnxGenAI -destination 'platform=iOS Simulator,name=iPhone 17' test` — unit tests mock `ChatInference`, so no model is required.
- Device note: a 1B model needs ~1.5 GB RAM; run on a physical device for realistic TTFT / tokens-per-sec.
