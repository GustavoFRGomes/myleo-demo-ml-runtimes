# Android LLM — LiteRT-LM

- Runtime: `com.google.ai.edge.litertlm:litertlm-android:0.17.1` (Google Maven), Kotlin `Engine`/`Conversation` API, **CPU backend by default**
  - GPU backend requires an OpenCL driver and is not available on all devices (missing driver → `UNKNOWN: Can not find OpenCL library on this device` at generation). Pass `Backend.GPU()` to `LiteRTLMEngine` on an OpenCL-capable device.
- Model format: `.litertlm` bundle (weights + tokenizer + chat template)
- Scope contract (F-LLM-1…5):
  - Download the model in-app, then load; show model id + load state + errors
  - Stream tokens for a prompt with temperature / top-k / top-p / max-tokens
  - Cancel / regenerate / clear, plus error and empty states
  - Report TTFT + tokens/sec per generation
  - Compose chat: system prompt + params sheet, streaming thread, info sheet
- Model: **Phi-4-mini-instruct** (MIT) — https://huggingface.co/litert-community/Phi-4-mini-instruct, `Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm` (~3.9 GB). Same base model as the ONNX leaf. Never committed.
- Delivery: first launch downloads the file to `filesDir/models/` (`HttpModelDownloader`, byte progress + cancel). No manual step.
- Architecture (MVVM): `ChatInference` seam over the SDK; `LiteRTLMEngine` (Engine/Conversation, `sendMessageAsync` → `Flow<Message>`, cancel via `cancelProcess()`, token count via `getTokenCount()`); `ChatViewModel` exposes `StateFlow<ChatUiState>`; thin `MainActivity` + Compose.
- Build/run: `./gradlew installDebug`. Tests: `./gradlew :app:testDebugUnitTest` — unit tests mock `ChatInference`/`ModelDownloader`, so no model is required.
- Device note: a 3.8B q8 model needs ~4 GB RAM; run on a physical device for realistic TTFT / tokens-per-sec.
