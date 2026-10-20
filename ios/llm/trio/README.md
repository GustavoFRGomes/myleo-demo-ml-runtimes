# iOS LLM — Trio (LiteRT-LM + ONNX GenAI + Core ML)

One app, three LLM runtimes behind a picker. All three are **MiniCPM5-1B** in each
runtime's own format (LiteRT-LM `.litertlm`, ONNX GenAI, Core ML 4-bit — the Core ML
leg was already MiniCPM5-1B, since no Phi-4-mini Core ML export fits its hand-wired
engine). Models download in-app on first open. Mirrors `ios/vector-search/trio`.

- Runtimes:
  - **LiteRT-LM** — `litert-community/MiniCPM5-1B` W4 `_gpu_opt` `.litertlm` (~793 MB); SPM `LiteRTLM`
  - **ONNX GenAI** — `microsoft/Phi-4-mini-instruct-onnx` int4 (~4.9 GB); local pod `onnxruntime-genai-c`
  - **Core ML** — `Reza2kn/MiniCPM5-1B-CoreML-4bit` 4-bit `.mlpackage` (~619 MB), 128-token context; SPM `swift-transformers`
- Structure:
  - `Domain/` — shared chat contract (`ChatInference`, `ChatMessage`, `GenerationParams/Metrics`, `ChatTranscript`) + `InferenceRuntime`
  - `Data/` — one engine + model locator/downloader per runtime (`LiteRTLMEngine`, `OnnxGenAIEngine`, `MiniCPMCoreMLEngine`)
  - `UI/` — `RuntimeSession` (caches a `ChatStore` per runtime), `RuntimePickerScreen`, `ChatScreen` (streaming, settings, info FAB, transcript export)
- Scope contract (F-LLM-1…5): runtime picker; load/state/errors; stream with temperature/top-k/top-p/max-tokens; cancel/regenerate/clear; TTFT + tokens/sec; SwiftUI chat; Markdown/JSON export. Per-runtime gaps are documented in each leaf's README (Core ML is 128-token/limited; ONNX/Core ML token counts differ).
- Model parity: see `docs/llm-model-coverage.md` for which checkpoint runs where, and `docs/benchmarks.md` for measured speed.
- Dependencies: **SPM** (`LiteRTLM` `0.18.0`, `swift-transformers` `1.3.4`) + **CocoaPods** (`onnxruntime-genai-c` via `podspec/onnxruntime-genai-c.podspec`, since it isn't on trunk) + a bridging header for the ONNX C API.
- Build/run (order matters): `xcodegen generate`, then `pod install`, then `open MyLeoLLMTrio.xcworkspace`. The LiteRT-LM package repo tracks LFS files, so build with `GIT_LFS_SKIP_SMUDGE=1` (or `brew install git-lfs`). Tests: `GIT_LFS_SKIP_SMUDGE=1 xcodebuild -workspace MyLeoLLMTrio.xcworkspace -scheme MyLeoLLMTrio -destination 'platform=iOS Simulator,name=iPhone 17' test` — unit tests mock `ChatInference`, so no model is required.
- API note: `LiteRTLM` is pinned to `0.18.0`, where model metadata is `ModelInfo` (renamed from `Capabilities` in `0.17.1`).
