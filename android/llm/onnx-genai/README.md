# Android LLM — ONNX GenAI

- Runtime: ONNX Runtime GenAI `0.17.0` Android AAR (`ai.onnxruntime.genai` Java API), **CPU only** — what the artifact does and does not allow is in *Acceleration on Android* below.
- Model format: ORT GenAI package — `model.onnx` (+ `.data`) + `tokenizer.json` + `tokenizer_config.json`, plus a `genai_config.json` this leaf synthesizes (the published package omits it).
- Scope contract (F-LLM-1…5): download + load with state/errors; stream with temperature/top-k/top-p/max-tokens; cancel/regenerate/clear; TTFT + tokens/sec; Compose chat (system prompt + params sheet, streaming thread, info sheet). Same UI as the `litert-lm` leaf.
- Model: **MiniCPM5-1B int4** (Apache-2.0) — https://huggingface.co/anydaytv/minicpm5-1b-onnx-int4, four files at the repository root (~1.41 GB; `model.onnx` sha256 `9fd8adb…`, `model.onnx.data` sha256 `63000fd…`). Same checkpoint as the `litert-lm` and `coreml` leaves. Never committed — download map in `docs/minicpm5-1b.md`.
- Delivery: first launch downloads 4 files to `filesDir/models/minicpm5-1b-onnx-int4/` (`OnnxFileSetDownloader`, aggregate byte progress + cancel), then writes `genai_config.json` next to them (`MiniCPM5GenAIConfig`).
- Prompt: `MiniCPM5ChatTemplate` renders the model's own `chat_template.jinja` by hand — `<s><|im_start|>system\n…<|im_end|>\n…<|im_start|>assistant\n<think>\n\n</think>\n\n`, i.e. thinking disabled exactly as the `litert-lm` leaves run. `Tokenizer.applyChatTemplate` is avoided: that JNI path corrupts the heap on device in 0.17.0.
- Architecture (MVVM): `ChatInference` seam → `OnnxGenAIEngine` (Java `Model`/`Tokenizer`/`GeneratorParams`/`Generator` + `TokenizerStream`); shared `ChatViewModel`/Compose UI.
- SDK note: `onnxruntime-genai` is **not on Maven Central**. `app/build.gradle.kts` registers a `downloadGenAiAar` task that fetches the release AAR into `app/libs/` (gitignored) before compile.
- Build/run: `./gradlew installDebug` (fetches the AAR once). Tests: `./gradlew :app:testDebugUnitTest` — unit tests mock `ChatInference`/`ModelDownloader`, so no model is required.
- Device note: int4 weights are ~1.32 GiB of `MatMulNBits` payload. The KV cache is fp32 and static: `max_length × 24 × 2 × 128 × 4 B` per tensor, so 1024 tokens ≈ 50 MB. Run on a physical device (≥6 GB RAM) for realistic TTFT / tokens-per-sec.

## Model choice

The generic fp16 export (`onnx-community/MiniCPM5-1B`) **cannot decode on any released ONNX
Runtime**. It expands `repeat_kv` outside the attention op in fp16, so `CastFloat16Transformer`
wraps all 24 `MultiHeadAttention` nodes in 200 precision casts, and the allocation planner sizes
those buffers from the *prefill* length. The first generated token then throws
`Shape mismatch attempting to re-use buffer. {1,1,2048} != {1,16,2048}` at
`layers.1/attn/v_proj/repeat_kv/Reshape_4` — reproduced with plain ORT (no GenAI) on macOS with
both `1.30.0` and `1.31.0`, and unresponsive to `enable_mem_pattern=false` or
`graph_optimization_level=ORT_DISABLE_ALL` (both verified applied). Upstream fix is
[onnxruntime#32717](https://github.com/microsoft/onnxruntime/pull/32717), merged 2026-10-15 — six
days after `1.31.0` shipped, so no release contains it.

The package this leaf ships is the `onnxruntime-genai` **model builder** export instead: 275 nodes
instead of 1687, 24 fused `GroupQueryAttention` ops, int4 `MatMulNBits` weights with **fp32**
activations. No casts are inserted, and the KV cache is a single static buffer
(`past_present_share_buffer: true`), so the planner never sees a shape change. Built with
`-p int4 -e cpu --extra_options accuracy_level=4 block_size=32`; measured ~110 tok/s (CPU) on
host across prompts of 3–16 tokens.

## Tokenizer patch

The int4 package ships the same `tokenizer.json` as the fp16 export (sha256 `3e065a55…`,
9,894,271 B), and it does not load in ORTX as published: its second BPE `Split`
uses `\p{N}+`, which is missing from ORTX's fixed branch menu, so the leftover is compiled
with `std::regex` and the model fails with `Invalid regex: \p{N}+`. `TokenizerPatch` rewrites
that single occurrence to the equivalent `\p{N}{1,3}` (+4 bytes) before `Model(...)` is
constructed. It is idempotent, keeps both `Split` steps and every token id intact, and is
covered by `TokenizerPatchTest`. Root cause and cross-tokenizer verification:
`docs/llm-model-coverage.md`.

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
  pins). The int4 package this leaf ships is a CPU/CUDA builder export and **no QNN export** exists
  for MiniCPM5-1B, and QNN context binaries are SoC-arch-locked, so they would not transfer to
  another device anyway.
- **`webgpu` needs no extra native dependency.** The stock AAR already links Dawn's Vulkan backend
  (references `libvulkan.so`, `VK_KHR_android_surface`, `dawn::native::vulkan::Device`) and GenAI has
  a `WebGPU` adapter, so it is the one cheap candidate: `appendProvider("webgpu")` against the int4
  package this leaf already ships (~1.41 GB) carries the same pinned-GPU memory risk the `litert-lm`
  leaf gates on. Not yet tried or measured; the EP's op coverage is partial, so unsupported nodes
  fall back to CPU with a device↔host copy each — that must be measured, not assumed. Note the
  builder export was produced with `-e cpu`, so `MatMulNBits` carries CPU-targeted packing; a WebGPU
  run would need a `-e webgpu` rebuild to be meaningful.

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
