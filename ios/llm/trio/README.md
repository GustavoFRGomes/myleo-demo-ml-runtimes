# iOS LLM — Trio (LiteRT-LM + ONNX GenAI + Core ML)

One app, three LLM runtimes behind a picker. All three use the same base model
(**MiniCPM5-1B**) in each runtime's own format; models download in-app on first
open. Mirrors `ios/vector-search/trio`.

- Runtimes:
  - **LiteRT-LM** — `litert-community/MiniCPM5-1B` W4 `.litertlm` (~793 MB); SPM `LiteRTLM`
  - **ONNX GenAI** — `anydaytv/minicpm5-1b-onnx-int4` + `onnx-community` config (~1.42 GB); local pod `onnxruntime-genai-c`
  - **Core ML** — `Reza2kn/MiniCPM5-1B-CoreML-4bit` 4-bit `.mlpackage` (~619 MB), 128-token context; SPM `swift-transformers`
- Structure:
  - `Domain/` — shared chat contract (`ChatInference`, `ChatMessage`, `GenerationParams/Metrics`, `ChatTranscript`) + `InferenceRuntime`
  - `Data/` — one engine + model locator/downloader per runtime (`LiteRTLMEngine`, `OnnxGenAIEngine`, `MiniCPMCoreMLEngine`)
  - `UI/` — `RuntimeSession` (caches a `ChatStore` per runtime), `RuntimePickerScreen`, `ChatScreen` (streaming, settings, info FAB, transcript export)
- Scope contract (F-LLM-1…5): runtime picker; load/state/errors; stream with temperature/top-k/top-p/max-tokens; cancel/regenerate/clear; TTFT + tokens/sec; SwiftUI chat; Markdown/JSON export. Per-runtime gaps are documented in each leaf's README (Core ML is 128-token/limited; ONNX/Core ML token counts differ).
- Model parity: same base checkpoint, different quantizers — **close copy, not byte-identical** (LiteRT W4 vs ONNX int4 vs Core ML int4). See `docs/benchmarks.md`.
- Dependencies: **SPM** (`LiteRTLM` `0.17.1`, `swift-transformers` `1.3.4`) + **CocoaPods** (`onnxruntime-genai-c` via `podspec/onnxruntime-genai-c.podspec`, since it isn't on trunk) + a bridging header for the ONNX C API.
- Build/run (order matters): `xcodegen generate`, then `pod install`, then `open MyLeoLLMTrio.xcworkspace`. The LiteRT-LM package repo tracks LFS files, so build with `GIT_LFS_SKIP_SMUDGE=1` (or `brew install git-lfs`). Tests: `GIT_LFS_SKIP_SMUDGE=1 xcodebuild -workspace MyLeoLLMTrio.xcworkspace -scheme MyLeoLLMTrio -destination 'platform=iOS Simulator,name=iPhone 17' test` — unit tests mock `ChatInference`, so no model is required.
