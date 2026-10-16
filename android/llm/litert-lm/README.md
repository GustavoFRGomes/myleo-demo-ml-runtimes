# Android LLM — LiteRT-LM

- Runtime: `com.google.ai.edge.litertlm:litertlm-android:0.17.1` (Google Maven), Kotlin `Engine`/`Conversation` API
  - **GPU first, CPU fallback.** `LiteRTLMEngine` tries `Backend.GPU()` then `Backend.CPU()`; the backend that actually initialized is reported in the info sheet. GPU needs an OpenCL driver — devices without one fail with `UNKNOWN: Can not find OpenCL library on this device`, which the fallback catches and re-runs on CPU. The manifest requests `libOpenCL.so` / `libvndksupport.so` (`required="false"`).
  - **GPU is skipped when the device is already known to be unable to hold it** — see *GPU feasibility gate* below. NPU not enabled: `Backend.NPU` needs Qualcomm QNN native libraries bundled in the app *and* a `.litertlm` built for the specific SoC; the generic Phi-4-mini download below is neither, so this leaf ships CPU/GPU only.
- Model format: `.litertlm` bundle (weights + tokenizer + chat template)
- Scope contract (F-LLM-1…5):
  - Download the model in-app, then load; show model id + load state + errors
  - Stream tokens for a prompt with temperature / top-k / top-p / max-tokens
  - Cancel / regenerate / clear, plus error and empty states
  - Report TTFT + tokens/sec per generation
  - Compose chat: system prompt + params sheet, streaming thread, info sheet
- Model: **Phi-4-mini-instruct** (MIT) — https://huggingface.co/litert-community/Phi-4-mini-instruct, `Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm` (~3.9 GB). Same base model as the ONNX leaf. Never committed.
- Delivery: first launch downloads the file to `filesDir/models/` (`HttpModelDownloader`, byte progress + cancel). No manual step.
- Architecture (MVVM): `ChatInference` seam over the SDK; `LiteRTLMEngine` (Engine/Conversation, `sendMessageAsync` → `Flow<Message>`, cancel via `cancelProcess()`, token count via `getTokenCount()`; backend selection via `planBackends` — the feasibility gate below, then `firstWorkingBackend` GPU → CPU); `ChatViewModel` exposes `StateFlow<ChatUiState>`; thin `MainActivity` + Compose.
- Build/run: `./gradlew installDebug`. Tests: `./gradlew :app:testDebugUnitTest` — unit tests mock `ChatInference`/`ModelDownloader`, so no model is required.
- Device note: a 3.8B q8 model needs ~4 GB RAM on CPU, but **~10 GB to attempt GPU** (see the gate below); run on a physical device for realistic TTFT / tokens-per-sec.

## GPU feasibility gate

On the GPU backend the model sits in GPU memory **plus** a private, non-file-backed copy of its
weights, which the kernel cannot reclaim. Measured with Phi-4-mini q8 (~3.9 GB), a successful GPU load
peaked at **~6.1 GB of graphics memory** (`GL mtrack`, ~7.4 GB total PSS), where CPU stayed on the
`mmap`ed file. On a device with less RAM the load is killed outright (`SIGKILL`) — which **no
`try/catch` can catch**, so the in-process fallback above is not enough on its own.

`LiteRTLMEngine` therefore decides *before* attempting GPU (`GpuPolicy` + `GpuPreflight`):

| Signal | Source | Effect |
|---|---|---|
| Total RAM < `2 × model + 1.5 GiB` | `ActivityManager.MemoryInfo.totalMem` | skip GPU → CPU |
| Remembered bad verdict | `SharedPreferences` `gpu_policy/gpu_verdict` | skip GPU → CPU |

The verdict is committed **before** `initialize()`, so a run that dies mid-load leaves `PENDING`; the
next launch folds that into `BAD`. A device that is too small, or has failed once, never retries GPU
again — clear the app data to reset. Measured: TCL T807D (7.6 GB) is below the gate and goes straight
to CPU; Galaxy Z Fold7 (12 GB) passes it and loads GPU in ~12 s. Same-device GPU vs CPU numbers
(decode ~1.8× faster on GPU; CPU loads ~17× faster) are in `docs/benchmarks.md`.
