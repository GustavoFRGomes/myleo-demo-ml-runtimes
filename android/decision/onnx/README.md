# Android Decision — ONNX Runtime

Julia-1 as a **decision model**: a state, a typed question and 2–20 candidate answers in, one
probability per answer out from a single forward pass. Same contract as `android/decision/litert`;
the ONNX export bakes the embedding table into the graph, so the runtime takes token **ids** and
there is no host-side token table. No text generation, no streaming, no TTFT.

- Runtime: ONNX Runtime for Android `1.30.0` (`com.microsoft.onnxruntime:onnxruntime-android`), Kotlin `2.4.10`, Compose (Material 3)
- Model format: `model.onnx` + external weights `model.onnx.data` + the checkpoint's `tokenizer.json` (BPE, 256,000 entries)
- Scope contract (F-DEC-1…5)
  - Download + open the package in-app; show backend, window, load timings, state and errors
  - One request = state text + question type (`choice` / `score` / `noul`) + 2–20 options
  - Strict encoding: nothing is truncated; a request that does not fit 1,024 tokens raises `EncodingException`
  - Answer: winning option (`choice`), expected zero-based rubric index (`score`), P(true) (`noul`) — plain softmax at temperature 1
  - Compose form with presets, question/option editor, probability bars, per-stage timings, cancel/retry/error states
  - Built-in **18-prompt suite** (8 multi-answer, 5 score, 5 yes/no) run in order, with a benchmark report: per-prompt + per-type timings, device, battery charge delta, and Markdown/JSON export to Downloads
- Model: **Julia-1** (Apache-2.0, 144.3M parameters, mmBERT-small encoder + decision head) — [`SupersonicLabs/Julia-1-ONNX`](https://huggingface.co/SupersonicLabs/Julia-1-ONNX) (revision `82a2fadf8fccfccdc5fd4e1009ba8f1a265eb7a8`), the author's export of [`SupersonicLabs/Julia-1`](https://huggingface.co/SupersonicLabs/Julia-1):
  - `model.onnx` — 2,988,923 B (graph only)
  - `model.onnx.data` — 576,782,336 B (external weights, including the 256,000 × 384 table; must stay next to the graph)
  - `tokenizer.json` — 34,363,188 B, sha256 `609d8f4c067cd3950f88594c5a802616cea245823836ef5848ee4fc40aab5b6f` — **byte-identical** to the LiteRT package's tokenizer
  - Total 614,134,447 B. Never committed: one in-app download into `files/models/julia-1-onnx/`. The repo publishes sizes + weight provenance but no `SHA256SUMS`, so the downloader enforces each file's byte length.
- Graph contract (verified against the export script and the published graph):
  - inputs `input_ids` int64 `[1, T]`, `attention_mask` int64 `[1, T]`, `marker_pos` int64 `[1, N]`, `marker_mask` bool `[1, N]`, `qtype` int64 `[1]`
  - output `logits` float `[1, N]`, one value per option
  - `T` is padded up to a multiple of 8 like the author's browser host; the attention mask is zero on the padding; marker positions are absolute token indices, so padding never moves them
- Backend: **CPU only**. The published export targets ONNX Runtime **WebGPU in a browser**; no Android execution provider for this graph has been validated (NNAPI is untried and the WebGPU path needs a browser runtime). Unlike the LiteRT leaf there is no FP32 GPU path to offer, so nothing runs at reduced precision.
- Encoding: same strict marker sequence as the LiteRT leaf (`<bos> "{type} question: {instructions}" <eos>`, `<mask>` + option per option, `<eos> state <eos>`) with window 1,024 and head budget 512. Because the tokenizer is the same file, **any request both leaves accept produces identical token ids**, so the two runtimes can be compared answer-for-answer.
- Timings: `prepare` (encode + sequence), `graph` split into tensor write / `session.run` / readback, `decode`, total; plus token count, window and tokenizer/session load.
- Architecture (MVVM): `Decider` seam (`load` / `decide`); `JuliaEngine` → `JuliaTokenizer` → `JuliaSequenceBuilder` → `JuliaInputs` → `OnnxGraphRunner` (`OrtSession`) → `JuliaDecoder`; `DecisionViewModel` exposes `StateFlow<DecisionUiState>`; `MainActivity` only wires the factory.
- Build/run: `./gradlew installDebug` from this folder (no model needed to build). Tests: `./gradlew :app:testDebugUnitTest` — 110 JVM tests cover the decoder, strict builder, tokenizer, input packing, locator, presets, suite/report formatting and ViewModel; no model or device required.
- Attribution: `JuliaJson`/`JuliaTokenizer` are adapted from the Apache-2.0 `Julia-1-LiteRT` Android sample (SPDX headers kept); encoding and answer semantics follow the author's `julia/data.py` / `julia/typed.py` contract.
