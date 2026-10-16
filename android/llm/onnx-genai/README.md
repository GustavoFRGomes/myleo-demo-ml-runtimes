# Android LLM — ONNX GenAI

- Runtime: ONNX Runtime GenAI `0.17.0` Android AAR (`ai.onnxruntime.genai` Java API), **CPU only** — what the artifact does and does not allow is in *Acceleration on Android* below.
- Model format: ORT GenAI package — `genai_config.json` + `model.onnx` (+ `.data`) + `tokenizer.json` + `chat_template.jinja`
- Scope contract (F-LLM-1…5): download + load with state/errors; stream with temperature/top-k/top-p/max-tokens; cancel/regenerate/clear; TTFT + tokens/sec; Compose chat (system prompt + params sheet, streaming thread, info sheet). Same UI as the `litert-lm` leaf.
- Model: **Phi-4-mini-instruct int4** (MIT) — https://huggingface.co/microsoft/Phi-4-mini-instruct-onnx, `cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/` (~4.9 GB). Same base model as the LiteRT-LM leaf. Never committed.
- Delivery: first launch downloads 7 files to `filesDir/models/phi-4-mini-instruct-onnx-int4/` (`OnnxFileSetDownloader`, aggregate byte progress + cancel).
- Architecture (MVVM): `ChatInference` seam → `OnnxGenAIEngine` (Java `Model`/`Tokenizer`/`GeneratorParams`/`Generator` + `TokenizerStream`, chat template via `Tokenizer.applyChatTemplate`); shared `ChatViewModel`/Compose UI.
- SDK note: `onnxruntime-genai` is **not on Maven Central**. `app/build.gradle.kts` registers a `downloadGenAiAar` task that fetches the release AAR into `app/libs/` (gitignored) before compile.
- Build/run: `./gradlew installDebug` (fetches the AAR once). Tests: `./gradlew :app:testDebugUnitTest` — unit tests mock `ChatInference`/`ModelDownloader`, so no model is required.
- Device note: a 3.8B int4 model needs ~4.5 GB RAM; run on a physical device for realistic TTFT / tokens-per-sec.

## Acceleration on Android

This leaf being CPU-only is a **model-artifact** limit, not a missing API:

- **The API exists.** `ai.onnxruntime.genai.Config` in the 0.17.0 AAR exposes `clearProviders()`,
  `appendProvider(String)` and `setProviderOption(provider, key, value)`, so `OnnxGenAIEngine` can
  name a provider. (The earlier claim in this file that the AAR "exposes no execution-provider
  choice" was wrong.)
- **Only two providers can run on Android.** GenAI's registry is its `src/ep/<name>` set — `cpu`,
  `cuda`, `dml`, `nvtensorrtrtx`, `openvino`, `qnn`, `ryzenai`, `vitisai`, `amdgpu`, `webgpu`.
  Of these, only **`qnn`** (Qualcomm NPU/HTP) and **`webgpu`** are plausible on a phone. `nnapi` and
  `xnnpack` *are* compiled into ORT but have no GenAI adapter, so they cannot be selected.
- **`qnn` is blocked on artifacts, not on code.** The stock `onnxruntime-android` AAR ships only
  `libonnxruntime.so` + `libonnxruntime4j_jni.so`; the Qualcomm backend libraries live in the
  separate `onnxruntime-android-qnn` artifact (currently `1.29.0`, one behind the `1.30.0` this leaf
  pins). More decisively, **no QNN export of Phi-4-mini exists in GenAI format**: the upstream repo
  publishes `cpu_and_mobile/*` and `gpu/gpu-int4-rtn-block-32` only, and the Phi-4 QNN models on
  Hugging Face are Windows Copilot+PC (Foundry Local) packages — no `genai_config.json`, compiled
  for a different HTP. QNN context binaries are SoC-arch-locked, so they do not transfer to our
  device.
- **`webgpu` needs no extra native dependency.** The stock AAR already links Dawn's Vulkan backend
  (references `libvulkan.so`, `VK_KHR_android_surface`, `dawn::native::vulkan::Device`) and GenAI has
  a `WebGPU` adapter, so it is the one cheap candidate: `appendProvider("webgpu")` against
  `gpu/gpu-int4-rtn-block-32` (~3.4 GB), carrying the same pinned-GPU memory risk the `litert-lm`
  leaf gates on. Not yet tried or measured.

Net: speeding this leaf up means shipping a model built for the accelerator — not building a custom
AAR, and not flipping a delegate. Until then it stays the CPU reference for the ONNX runtime. (The
`litert-lm` leaves run GPU in-box because LiteRT-LM bundles the backend itself.)

