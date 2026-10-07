# Android LLM — Dual (LiteRT-LM + ONNX GenAI)

- Runtimes: LiteRT-LM `0.17.1` (`litertlm-android`) **and** ONNX Runtime GenAI `0.17.0` (Android AAR), both CPU
- Model format: `.litertlm` (LiteRT-LM) and ORT GenAI package — `genai_config.json` + `model.onnx` (+ `.data`) + tokenizer (ONNX GenAI)
- Scope contract (F-LLM-1…5): the same chat screen as the single-runtime leaves, plus a runtime picker.
  - `FilterChip` picker switches LiteRT-LM ⇄ ONNX GenAI; only one engine is resident, the previous one is closed on switch, and the thread resets
  - Per-runtime locate/download/load, streaming with temperature/top-k/top-p/max-tokens, cancel/regenerate/clear, TTFT + tokens/sec
- Model: **Phi-4-mini-instruct** (MIT) on both runtimes — the same base model as the other LLM leaves. Never committed.
  - LiteRT-LM: https://huggingface.co/litert-community/Phi-4-mini-instruct → `Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm` (~3.9 GB)
  - ONNX GenAI: https://huggingface.co/microsoft/Phi-4-mini-instruct-onnx → `cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/` (~4.9 GB)
  - Each runtime keeps its own `filesDir/models/…` folder, so switching does not re-download.
- Architecture (MVVM): one `ChatInference` seam with two engines (`LiteRTLMEngine`, `OnnxGenAIEngine`); a `RuntimeBinding` per `RuntimeOption` supplies locate/inference/downloader; `ChatViewModel` swaps bindings and releases the old engine.
- Build/run: `./gradlew installDebug` (fetches the ONNX GenAI AAR once). Tests: `./gradlew :app:testDebugUnitTest` — unit tests mock `ChatInference`/`ModelDownloader`, so no model is required.
- Device note: only one 3.8B model is resident at a time (~4 GB RAM); switching releases the previous engine.
