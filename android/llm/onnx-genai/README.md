# Android LLM — ONNX GenAI

- Runtime: ONNX Runtime GenAI `0.17.0` Android AAR (`ai.onnxruntime.genai` Java API), CPU
- Model format: ORT GenAI package — `genai_config.json` + `model.onnx` (+ `.data`) + `tokenizer.json` + `chat_template.jinja`
- Scope contract (F-LLM-1…5): download + load with state/errors; stream with temperature/top-k/top-p/max-tokens; cancel/regenerate/clear; TTFT + tokens/sec; Compose chat (system prompt + params sheet, streaming thread, info sheet). Same UI as the `litert-lm` leaf.
- Model: **Phi-4-mini-instruct int4** (MIT) — https://huggingface.co/microsoft/Phi-4-mini-instruct-onnx, `cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/` (~4.9 GB). Same base model as the LiteRT-LM leaf. Never committed.
- Delivery: first launch downloads 7 files to `filesDir/models/phi-4-mini-instruct-onnx-int4/` (`OnnxFileSetDownloader`, aggregate byte progress + cancel).
- Architecture (MVVM): `ChatInference` seam → `OnnxGenAIEngine` (Java `Model`/`Tokenizer`/`GeneratorParams`/`Generator` + `TokenizerStream`, chat template via `Tokenizer.applyChatTemplate`); shared `ChatViewModel`/Compose UI.
- SDK note: `onnxruntime-genai` is **not on Maven Central**. `app/build.gradle.kts` registers a `downloadGenAiAar` task that fetches the release AAR into `app/libs/` (gitignored) before compile.
- Build/run: `./gradlew installDebug` (fetches the AAR once). Tests: `./gradlew :app:testDebugUnitTest` — unit tests mock `ChatInference`/`ModelDownloader`, so no model is required.
- Device note: a 3.8B int4 model needs ~4.5 GB RAM; run on a physical device for realistic TTFT / tokens-per-sec.
