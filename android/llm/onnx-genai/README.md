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

## 16 KB page size

Android 15+ flags native libraries whose `PT_LOAD` segments are not 16 KB-aligned (a 16 KB-page kernel
cannot load them at all). Read out of this leaf's built `app-debug.apk`:

- **Five of the six `arm64-v8a` libraries are compliant** (`p_align` `0x4000`): `libonnxruntime.so`
  and `libonnxruntime4j_jni.so` (ORT `1.30.0`), `libonnxruntime-genai.so` and
  `libonnxruntime-genai-jni.so` (GenAI `0.17.0`), `libandroidx.graphics.path.so`. (The `dual` leaf's
  APK adds `liblitertlm_jni.so`, also `0x4000`, and has the same single offender.)
- **Only `libmat.so` is `0x1000`.** The Android compatibility dialog lists the compliant ones as
  "Unknown error" and reserves `LOAD segment not aligned` for this one, so the warning overstates it.
- **`libmat.so` is Microsoft's 1DS telemetry SDK** (~28 MB), pulled into the GenAI release AAR by its
  `ENABLE_TELEMETRY` sub-build (`cmake/telemetry.cmake`). It is a hard `DT_NEEDED` of
  `libonnxruntime-genai.so`, so a Gradle `jniLibs` exclusion would break the load, and `PT_LOAD`
  alignment cannot be changed without relinking. The 16 KB flag GenAI does set for Android evidently
  does not reach that sub-project — the mismatch is measurable, not theoretical.
- **Nothing in this repo can fix it.** [onnxruntime#26490](https://github.com/microsoft/onnxruntime/issues/26490)
  is an open feature request about the ORT *source* build, where the flag is already applied — which is
  why the ORT libraries measure `0x4000`. The gap is in the GenAI build, and no release closes it: it
  arrives with telemetry in `0.15.0` and persists through `0.17.0`, the latest. Pinning the last
  pre-telemetry AAR (`0.14.0`) would drop `libmat.so` but also gives up `#2111` (*Fix
  AppendNextTokensToSequences heap overflow* — a `max_length`-boundary overflow write on the decode
  path) plus `0.15.0`'s other bounds-checking and use-after-free fixes, so `0.17.0` stays. A clean
  16 KB build would need GenAI built from source with `ENABLE_TELEMETRY=OFF`.

Impact today is nil on our hardware: the Fold7 runs a 4 KB kernel (`uname -r` ends `-4k`,
`getconf PAGE_SIZE` = `4096`) and the dialog only appears for debuggable builds. It turns real only on
a 16 KB-page device, where `libonnxruntime-genai.so` would fail to load.
