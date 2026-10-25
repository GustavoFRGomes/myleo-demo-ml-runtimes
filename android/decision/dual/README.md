# Android Decision — Dual (LiteRT + ONNX)

One Julia-1 decision screen with a runtime picker: download and load the **LiteRT** package or the
**ONNX** package, then run the same state + question + options request on either. Only one model is
resident at a time; switching releases the previous engine and clears results but keeps the edited
form, so the same request can be compared across runtimes.

- Runtimes: LiteRT `2.2.0` (`CompiledModel`, GPU FP32 → CPU fallback, windows 512/1024) **and** ONNX Runtime Android `1.30.0` (CPU, window 1024)
- Models: the same Julia-1 checkpoint in each runtime's format, in separate folders under `files/models/` (a switch never re-downloads)
  - **LiteRT** — [`litert-community/Julia-1-LiteRT`](https://huggingface.co/litert-community/Julia-1-LiteRT) (revision `8b3733e1…`): `julia1_s512_fp32.tflite` 185,095,348 B, `julia1_s1024_fp32.tflite` 188,503,220 B, `julia1_token_table_fp16.bin` 196,608,000 B, `tokenizer.json` 34,363,188 B — 604,569,756 B total
  - **ONNX** — [`SupersonicLabs/Julia-1-ONNX`](https://huggingface.co/SupersonicLabs/Julia-1-ONNX) (revision `82a2fadf…`): `model.onnx` 2,988,923 B, `model.onnx.data` 576,782,336 B, `tokenizer.json` 34,363,188 B — 614,134,447 B total
  - Both `tokenizer.json` files are byte-identical (sha256 `609d8f4c…`), so an accepted request produces the same token ids on either runtime. Never committed; sizes are enforced on download.
- Scope contract (F-DEC-1…5), plus the picker
  - Download + load each runtime on demand; show backend, window, load timings, state and errors per runtime
  - One request = state text + question type (`choice` / `score` / `noul`) + 2–20 options; strict encoding, never truncated
  - Answer: winning option (`choice`), expected rubric index (`score`), P(true) (`noul`); probabilities = softmax over the marker logits
  - Switching runtime swaps the `Decider`, closes the previous engine, resets results and reuses the printed form
  - Compose screen with runtime chips, presets, question/option editor, probability bars, per-stage timings, cancel/retry/error states
  - Built-in **18-prompt suite** (8 multi-answer, 5 score, 5 yes/no) run in order, with a benchmark report per runtime (per-prompt + per-type timings, device, battery charge delta, Markdown/JSON export) and a comparison card once both runtimes have a report
- Backends: LiteRT tries **GPU FP32** then CPU and reports which compiled; ONNX is **CPU only** (the published export targets browser WebGPU and no Android EP is validated). No fp16/NPU path in either lane.
- Architecture (MVVM): `DecisionViewModel` over one `RuntimeBinding` per `DecisionRuntime` (`LiteRtDecider` / `OnnxDecider` behind the `Decider` seam); `MainActivity` wires both bindings with their own locator + downloader. Editing, question construction and answer formatting are shared across lanes.
- Build/run: `./gradlew installDebug` from this folder (no model needed to build). Tests: `./gradlew :app:testDebugUnitTest` — 133 JVM tests, including runtime switching (engine swap, results reset, per-runtime downloader), suite runs, benchmark reports and both lanes' pure logic; no model or device required.
- Attribution: `JuliaJson`/`JuliaTokenizer` are adapted from the Apache-2.0 `Julia-1-LiteRT` Android sample (SPDX headers kept); encoding and answer semantics follow the author's `julia/data.py` / `julia/typed.py` contract.
