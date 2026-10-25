# Android Decision — LiteRT

Julia-1 is a **decision model, not a chat model**: it reads a state, a typed question and 2–20
candidate answers, then returns one probability per answer from a single forward pass. It never
generates text, so this leaf reports decision latency (`prepare` / `lookup` / `graph` / `decode`)
instead of TTFT and tokens-per-sec.

- Runtime: LiteRT `2.2.0` (`com.google.ai.edge.litert:litert`, `CompiledModel` API), Kotlin `2.4.10`, Compose (Material 3)
- Model format: `.tflite` graph with a static window + a **host-side** float16 token table + the checkpoint's `tokenizer.json`
- Scope contract
  - Download + load Julia-1 in-app; show backend, windows, load timings, state and errors
  - One request = state text + question type (`choice` / `score` / `noul`) + 2–20 options
  - Strict encoding: nothing is truncated; a request that does not fit the window raises `EncodingException`
  - Answer in the author's named-question terms: winning option (`choice`), expected zero-based rubric index (`score`), P(true) (`noul`) — plain softmax at temperature 1, no calibration
  - Per-request timings: tokenize/prepare, token-table lookup, graph call with readback, decode, total; plus `window`, token count and backend
  - Compose form with presets, question/option editor, probability bars, error and empty states
- Model: **Julia-1** (Apache-2.0, 144.3M parameters, mmBERT-small encoder + decision head) — base checkpoint [`SupersonicLabs/Julia-1`](https://huggingface.co/SupersonicLabs/Julia-1), LiteRT conversion [`litert-community/Julia-1-LiteRT`](https://huggingface.co/litert-community/Julia-1-LiteRT) (revision `8b3733e196c201ee32e8f5063211588c7f87dd7e`):
  - `julia1_s512_fp32.tflite` — 185,095,348 B, sha256 `be2d0b7d…` (required)
  - `julia1_s1024_fp32.tflite` — 188,503,220 B, sha256 `b4f26059…` (optional; compiled on first long request)
  - `julia1_token_table_fp16.bin` — 196,608,000 B, sha256 `5c7ad7bd…`, `[256000, 384]` little-endian float16, memory-mapped
  - `tokenizer.json` — 34,363,188 B, sha256 `609d8f4c…`, unchanged from the checkpoint
  - Never committed: downloaded into `files/models/julia-1-litert/` (604,569,756 B for both windows). A size mismatch fails the download instead of loading a truncated table.
- Request layout (strict): `<bos> "{type} question: {instructions}" <eos>` then one `<mask>` + option text per option, then `<eos> state <eos>`. The logit at each `<mask>` scores the option that follows it. Options are the criteria descriptions themselves; `noul` is ordered `false`, `true`.
- Backend: **GPU FP32 first, CPU fallback** (`CompiledModel.GpuOptions(precision = FP32)`; the GPU's default fp16 changes the answers, so no fp16 and no NPU path is offered). The info card reports which backend actually compiled.
- Context: the S512 graph is compiled at load. A request over 512 tokens uses the S1024 graph when installed; otherwise the strict-encoding error names the needed tokens. The tokenizer is 34 MB of JSON, so its first parse costs seconds — reported separately from the table and graph.
- Architecture (MVVM): `Decider` seam (`load` / `decide`, no token stream); `JuliaEngine` drives `JuliaTokenizer` → `JuliaSequenceBuilder` → `JuliaTokenTable.gather` → `JuliaGraphRunner` → `JuliaDecoder`; `DecisionViewModel` exposes `StateFlow<DecisionUiState>`; `MainActivity` only wires the factory. All native calls run on one LiteRT thread.
- Build/run: `./gradlew installDebug` from this folder (no model needed to build). Tests: `./gradlew :app:testDebugUnitTest` — 86 JVM tests cover the decoder, strict sequence builder, synthetic tokenizer, float16 table, locator, presets, formatting and ViewModel; no model or device required.
- Attribution: `JuliaJson` and `JuliaTokenizer` are adapted from the Apache-2.0 `Julia-1-LiteRT` Android sample (SPDX headers kept); the strict encoding and decoder follow the author's `julia/data.py` / `julia/typed.py` contract.
