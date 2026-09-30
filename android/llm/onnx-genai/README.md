# Android LLM — ONNX GenAI

- Runtime: ONNX Runtime GenAI `0.17.0` Android AAR (`ai.onnxruntime.genai` Java API), CPU
- Model format: ORT GenAI package — `genai_config.json` + `model.onnx` (+ `.data`) + `tokenizer.json` + `chat_template.jinja`
- Scope contract (F-LLM-1…5): download + load with state/errors; stream with temperature/top-k/top-p/max-tokens; cancel/regenerate/clear; TTFT + tokens/sec; Compose chat (system prompt + params sheet, streaming thread, info sheet). Same UI as the `litert-lm` leaf.
- Model: **MiniCPM5-1B int4** (Apache-2.0) — weights from https://huggingface.co/anydaytv/minicpm5-1b-onnx-int4 (int4 MatMulNBits, block 32, fp32 activations, ~1.42 GB), config from https://huggingface.co/onnx-community/MiniCPM5-1B (`genai_config.json` + `chat_template.jinja`). Same files as the iOS `onnx-genai` leaf — **close copy, not byte-identical** to the LiteRT W4 model (different quantizer). Never committed.
- Delivery: first launch downloads the 6 files to `filesDir/models/minicpm5-1b-onnx-int4/` (`OnnxFileSetDownloader`, aggregate byte progress + cancel).
- Architecture (MVVM): `ChatInference` seam → `OnnxGenAIEngine` (Java `Model`/`Tokenizer`/`GeneratorParams`/`Generator` + `TokenizerStream`, chat template via `Tokenizer.applyChatTemplate`); shared `ChatViewModel`/Compose UI.
- SDK note: `onnxruntime-genai` is **not on Maven Central**. `app/build.gradle.kts` registers a `downloadGenAiAar` task that fetches the release AAR into `app/libs/` (gitignored) before compile.
- Build/run: `./gradlew installDebug` (fetches the AAR once). Tests: `./gradlew :app:testDebugUnitTest` — unit tests mock `ChatInference`/`ModelDownloader`, so no model is required.
- Device note: ~1.5 GB RAM; run on a physical device for realistic TTFT / tokens-per-sec.
