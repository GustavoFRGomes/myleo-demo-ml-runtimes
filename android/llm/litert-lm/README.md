# Android LLM — LiteRT-LM

- Runtime: `com.google.ai.edge.litertlm:litertlm-android:0.18.0` (Google Maven), Kotlin `Engine`/`Conversation` API
  - **GPU first, CPU fallback.** `LiteRTLMEngine` tries `Backend.GPU()` then `Backend.CPU()`; the backend that actually initialized is reported in the info sheet. GPU needs an OpenCL driver — devices without one fail with `UNKNOWN: Can not find OpenCL library on this device`, which the fallback catches and re-runs on CPU. The manifest requests `libOpenCL.so` / `libvndksupport.so` (`required="false"`).
  - **GPU is skipped when the device is already known to be unable to hold it** — see *GPU feasibility gate* below. NPU not enabled: `Backend.NPU` needs Qualcomm QNN native libraries bundled in the app *and* a `.litertlm` built for the specific SoC; the generic MiniCPM5 download below is neither, so this leaf ships CPU/GPU only.
  - **Model metadata** comes from `ModelInfo.from(path)` (0.18.0), which reads the bundle without loading the weights; the info sheet shows the context length it reports.
- Model format: `.litertlm` bundle (weights + tokenizer + chat template)
- Scope contract (F-LLM-1…5):
  - Download the model in-app, then load; show model id + load state + errors
  - Stream tokens for a prompt with temperature / top-k / top-p / max-tokens
  - Cancel / regenerate / clear, plus error and empty states
  - Report TTFT + tokens/sec per generation
  - Compose chat: system prompt + params sheet, streaming thread, info sheet
- Model: **MiniCPM5-1B** (Apache-2.0) — https://huggingface.co/litert-community/MiniCPM5-1B, `minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm` (~793 MB, mixed INT4-block32/INT8 W4 with FP32 activations). Same base checkpoint as the other LLM leaves. Needs LiteRT-LM ≥ 0.18.0. Never committed.
- Context window: **1024 tokens**, as declared by the bundle and reported by `ModelInfo.maxContextTokens()` (info sheet → "Context: 1024 tokens"). That is the pack's own budget, not the checkpoint's — MiniCPM5-1B natively supports 131,072, but this `_gpu_opt` conversion declares 1024. Treat it as the ceiling for prompt + history + output: the 512-token default output leaves ~480 tokens for the prompt, so long documents and long chats have to be trimmed. (The ONNX leaf keeps the 131,072 declaration — see its README.)
- Delivery: first launch downloads the file to `filesDir/models/` (`HttpFileDownloader`, byte progress + cancel). No manual step.
- Architecture (MVVM): `ChatInference` seam over the SDK; `LiteRTLMEngine` (Engine/Conversation, `sendMessageAsync` → `Flow<Message>`, cancel via `cancelProcess()`, token count via `getTokenCount()`; backend selection via `planBackends` — the feasibility gate below, then `firstWorkingBackend` GPU → CPU); `ChatViewModel` exposes `StateFlow<ChatUiState>`; thin `MainActivity` + Compose.
- Build/run: `./gradlew installDebug`. Tests: `./gradlew :app:testDebugUnitTest` — unit tests mock `ChatInference`/`ModelDownloader`, so no model is required.
- Device note: a 1B W4 model loads in a couple of seconds and decodes far faster than the 3.8B q8 bundle this leaf used to ship; with the gate below it also clears every device we test on, so GPU is always attempted. Run on a physical device for realistic TTFT / tokens-per-sec.

## GPU feasibility gate

On the GPU backend the model sits in GPU memory **plus** a private, non-file-backed copy of its
weights, which the kernel cannot reclaim. The `2 × model + 1.5 GiB` shape was calibrated on Phi-4-mini
q8 (~3.9 GB), where a successful GPU load peaked at **~6.1 GB of graphics memory** (`GL mtrack`,
~7.4 GB total PSS) and CPU stayed on the `mmap`ed file. On a device with less RAM the load is killed
outright (`SIGKILL`) — which **no `try/catch` can catch**, so the in-process fallback above is not
enough on its own.

`LiteRTLMEngine` therefore decides *before* attempting GPU (`GpuPolicy` + `GpuPreflight`):

| Signal | Source | Effect |
|---|---|---|
| Total RAM < `2 × model + 1.5 GiB` | `ActivityManager.MemoryInfo.totalMem` | skip GPU → CPU |
| Remembered bad verdict | `SharedPreferences` `gpu_policy/gpu_verdict` | skip GPU → CPU |

The verdict is committed **before** `initialize()`, so a run that dies mid-load leaves `PENDING`; the
next launch folds that into `BAD`. A device that is too small, or has failed once, never retries GPU
again — clear the app data to reset.

With the shipped 793 MB bundle the gate needs **~3.0 GiB**, so every device we test on (Galaxy Z Fold7
12 GB, TCL T807D 7.6 GB) attempts GPU; the gate only bites for larger bundles. Measured Phi-4-mini GPU
vs CPU numbers are in `docs/benchmarks.md`.
