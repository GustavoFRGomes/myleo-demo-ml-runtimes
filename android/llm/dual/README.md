# Android LLM — Dual (LiteRT-LM + ONNX GenAI)

- Runtimes: LiteRT-LM `0.18.0` (`litertlm-android`) **and** ONNX Runtime GenAI `0.17.0` (Android AAR)
  - LiteRT-LM: **GPU first, CPU fallback** (`Backend.GPU()` → `Backend.CPU()`; manifest requests `libOpenCL.so`/`libvndksupport.so`). The info sheet shows the backend that actually loaded. GPU is skipped up front when the device is already known to be unable to hold it (total RAM below `2 × model + 1.5 GiB`, or a GPU load that previously died mid-load) — same `GpuPolicy`/`GpuPreflight` gate as the `litert-lm` leaf; see its README → *GPU feasibility gate*.
  - ONNX GenAI: **CPU only** — GenAI *could* be asked for `qnn`/`webgpu`, but no accelerated Phi-4-mini artifact for it exists in GenAI format and the WebGPU path is untried; see the `onnx-genai` leaf README → *Acceleration on Android*.
- Model format: `.litertlm` (LiteRT-LM) and ORT GenAI package — `genai_config.json` + `model.onnx` (+ `.data`) + tokenizer (ONNX GenAI)
- Scope contract (F-LLM-1…5): the same chat screen as the single-runtime leaves, plus a runtime picker.
  - `FilterChip` picker switches LiteRT-LM ⇄ ONNX GenAI; only one engine is resident, the previous one is closed on switch, and the thread resets
  - Per-runtime locate/download/load, streaming with temperature/top-k/top-p/max-tokens, cancel/regenerate/clear, TTFT + tokens/sec
- Model: **MiniCPM5-1B** (Apache-2.0) on the LiteRT-LM side and **Phi-4-mini-instruct** (MIT) on the ONNX GenAI side — never committed.
  - LiteRT-LM: https://huggingface.co/litert-community/MiniCPM5-1B → `minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm` (~793 MB, W4, needs LiteRT-LM ≥ 0.18.0)
  - ONNX GenAI: https://huggingface.co/microsoft/Phi-4-mini-instruct-onnx → `cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/` (~4.9 GB)
  - Each runtime keeps its own `filesDir/models/…` folder, so switching does not re-download.
- Architecture (MVVM): one `ChatInference` seam with two engines (`LiteRTLMEngine`, `OnnxGenAIEngine`); a `RuntimeBinding` per `RuntimeOption` supplies locate/inference/downloader; `ChatViewModel` swaps bindings and releases the old engine.
- Build/run: `./gradlew installDebug` (fetches the ONNX GenAI AAR once). Tests: `./gradlew :app:testDebugUnitTest` — unit tests mock `ChatInference`/`ModelDownloader`, so no model is required.
- Device note: only one model is resident at a time (MiniCPM5-1B ~793 MB on the LiteRT-LM side, Phi-4-mini int4 ~4.5 GB on the ONNX side); the 793 MB bundle clears the GPU gate (`2 × model + 1.5 GiB` ≈ 3.0 GiB) on every device we test on, and switching releases the previous engine.
