# MyLeo — On-Device Vector Search + LLM Monorepo

A monorepo of **on-device AI sample implementations** for iOS and Android, covering three problem areas across multiple inference runtimes:

1. **vector-search** — on-device text embeddings + similarity search (the retrieval half of RAG).
2. **llm** — on-device text generation / chat inference (the generation half of RAG).
3. **decision** — on-device structured decisions: a state + typed question + candidate answers → probabilities (Julia-1; no text generation).

Each `platform / area / runtime` combination is an independent, comparable variation. Same task, different technology — so you can compare integration effort, performance, model formats, and UX side by side.

## Structure

```text
.
├── README.md
├── AGENTS.md
├── ios/
│   ├── vector-search/
│   │   ├── litert/        # LiteRT embedding model + cosine/HNSW search (Swift)
│   │   ├── onnx/          # ONNX Runtime Mobile embedding model + vector search (Swift)
│   │   ├── coreml/        # Core ML embedding model + Accelerate search (Swift)
│   │   └── trio/          # Runtime chooser: LiteRT, ONNX or Core ML, lazy init, shared index (Swift)
│   └── llm/
│       ├── litert-lm/     # LiteRT-LM (.litertlm) chat inference (Swift)
│       ├── onnx-genai/    # ONNX Runtime GenAI (Phi / Llama / SmolLM) (Swift)
│       ├── coreml/        # Core ML / Apple Foundation Models inference (Swift)
│       └── trio/          # Runtime chooser: LiteRT-LM, ONNX GenAI or Core ML (Swift)
└── android/
    ├── vector-search/
    │   ├── litert/        # LiteRT Text Embedder + vector search (Kotlin)
    │   ├── onnx/          # ONNX Runtime Mobile embedding model + vector search (Kotlin)
    │   └── dual/          # Runtime chooser: LiteRT or ONNX, lazy init, shared index (Kotlin)
    ├── llm/
    │   ├── litert-lm/     # LiteRT-LM / MediaPipe LLM Inference API (Kotlin)
    │   ├── onnx-genai/    # ONNX Runtime GenAI (Kotlin)
    │   └── dual/          # Runtime chooser: LiteRT-LM or ONNX GenAI (Kotlin)
    └── decision/
        ├── litert/        # Julia-1 decision model (state + question + options → probabilities) (Kotlin)
        ├── onnx/          # Same Julia-1 contract on ONNX Runtime (Kotlin)
        └── dual/          # Runtime chooser: LiteRT or ONNX, per-runtime download + engine swap (Kotlin)
```

> Naming: `litert-lm` and `onnx-genai` are the LLM-specific runtime SDKs (chat/generation).
> Plain `litert` / `onnx` under `vector-search` and `decision` are the single-pass SDKs
> (embeddings / classification), which never stream tokens.
> `coreml` exists on iOS only — there is no Core ML on Android.

## Technology matrix

| Area | iOS | Android |
|---|---|---|
| vector-search / LiteRT | `ios/vector-search/litert` — LiteRT embedding (e.g. Universal Sentence Encoder / MiniLM `.tflite`), brute-force cosine or HNSW | `android/vector-search/litert` — LiteRT Text Embedder, same model family |
| vector-search / ONNX | `ios/vector-search/onnx` — ONNX Runtime Mobile + MiniLM/E5 `.onnx` | `android/vector-search/onnx` — ONNX Runtime (AAR) + same `.onnx` |
| vector-search / Core ML | `ios/vector-search/coreml` — Core ML `.mlpackage` embedding + Accelerate/BNNS | — (no Android equivalent) |
| vector-search / Trio | `ios/vector-search/trio` — picker for LiteRT, ONNX or Core ML at runtime, lazy init, shared index cleared on switch | — (Android `dual` covers LiteRT + ONNX only) |
| vector-search / Dual | — (no iOS equivalent) | `android/vector-search/dual` — picker for LiteRT or ONNX at runtime, lazy init, shared index cleared on switch |
| llm / LiteRT-LM | `ios/llm/litert-lm` — LiteRT-LM `.litertlm` bundle (Phi-4-mini-instruct), GPU delegate | `android/llm/litert-lm` — LiteRT-LM `Engine` API (Phi-4-mini), GPU-first with a RAM / known-failure gate, CPU fallback |
| llm / ONNX GenAI | `ios/llm/onnx-genai` — `onnxruntime-genai` (Obj-C/C++ bridge) running Phi-4-mini-instruct int4 `.onnx` | `android/llm/onnx-genai` — `onnxruntime-genai` AAR, same model family |
| llm / Core ML | `ios/llm/coreml` — Apple Foundation Models (system-hosted, where available) with Core ML `.mlpackage` fallback | — (no Android equivalent) |
| llm / Trio | `ios/llm/trio` — picker for LiteRT-LM, ONNX GenAI or Core ML (Phi-4-mini-instruct on the first two, MiniCPM5-1B on Core ML) | — (no Android equivalent) |
| llm / Dual | — (no iOS equivalent) | `android/llm/dual` — picker for LiteRT-LM or ONNX GenAI at runtime, both Phi-4-mini-instruct |
| decision / LiteRT | — (no iOS equivalent) | `android/decision/litert` — Julia-1 decision model (`litert-community/Julia-1-LiteRT`): state + typed question + 2–20 options → probabilities, GPU FP32 or CPU |
| decision / ONNX | — (no iOS equivalent) | `android/decision/onnx` — the same Julia-1 contract on ONNX Runtime (`SupersonicLabs/Julia-1-ONNX`), CPU, ids in / per-option logits out |
| decision / Dual | — (no iOS equivalent) | `android/decision/dual` — picker for LiteRT or ONNX with per-runtime download; switching releases the previous engine and keeps the edited question form |

## What each variation implements

### vector-search (`*/vector-search/*`)

Same contract, different runtime:

- Embed a chunk of text → float vector (normalized).
- Store vectors in a small on-device index.
- Query: top-k cosine similarity search with latency + score output.
- Minimal demo UI: ingest sample docs, type a query, see ranked hits.

Each runtime folder owns its model format, tokenizer, and delegate/accelerator setup. Shared logic (e.g. cosine math) must not be force-shared across runtimes — duplication is acceptable if it keeps variations independent.

Measured sizes, load/embed timings, and why runtimes differ: see [`docs/benchmarks.md`](docs/benchmarks.md).

### llm (`*/llm/*`)

Same contract, different runtime:

- Load a chat-capable model from app bundle / download.
- Stream tokens for a prompt with configurable temperature / top-k / top-p / max tokens.
- Minimal demo UI: system prompt + chat thread + tokens/sec + TTFT display.

Each runtime folder owns its model packaging (`.litertlm`, ONNX GenAI config + tokenizer, Core ML / Foundation Models wiring).

Which model each leaf uses, and whether one checkpoint can cover all three runtimes: see
[`docs/llm-model-coverage.md`](docs/llm-model-coverage.md). Where models come from per runtime,
how many actually load, and how to triage a candidate: [`docs/model_discovery.md`](docs/model_discovery.md).
Exact download URLs, file lists and sizes per runtime: [`docs/minicpm5-1b.md`](docs/minicpm5-1b.md).
iOS build, provisioning, packaging and accelerator caveats (Metal vs ANE, Core ML EP, context limits):
[`docs/ios-llm-hurdles.md`](docs/ios-llm-hurdles.md).
Battery and energy across demos: [`docs/benchmarks_batter.md`](docs/benchmarks_batter.md).
Interactive head-to-head dashboard (self-contained HTML): [`docs/benchmarks.html`](docs/benchmarks.html) — see [`docs/benchmarks-dashboard.md`](docs/benchmarks-dashboard.md) for Confluence embedding.

On Android, the LiteRT-LM leaves default to the **GPU** backend but skip it on a device already
known to be unable to hold it — total RAM below `2 × model + 1.5 GiB`, or a GPU load that previously
died mid-load — and fall back to CPU. GPU keeps a pinned, non-reclaimable weight copy (~6.1 GB for
Phi-4-mini q8) that can OOM-kill the app outright, so this is decided *before* attempting it. Details:
[`android/llm/litert-lm` → *GPU feasibility gate*](android/llm/litert-lm/README.md#gpu-feasibility-gate)
and [`docs/benchmarks.md`](docs/benchmarks.md).

The ONNX GenAI leaves are the mirror image. GenAI *can* be asked for `qnn` or `webgpu`, but no
accelerated Phi-4-mini exists in its model format and QNN context binaries are SoC-arch-locked, so
they stay CPU-only. Details:
[`android/llm/onnx-genai` → *Acceleration on Android*](android/llm/onnx-genai/README.md#acceleration-on-android).

### decision (`*/decision/*`)

Same contract, different runtime:

- One forward pass per request: state text + typed question (`choice` / `score` / `noul`) + 2–20 options → one probability per option.
- Strict encoding (reject, never truncate); answers reported as the winning option, the expected rubric index, or P(true).
- Demo UI: state + question editor, presets, probability bars, per-stage timings.
- **Suite mode**: 18 built-in prompts (8 multi-answer, 5 score, 5 yes/no) run in order, with a benchmark report — per-prompt and per-type timings, device, battery charge delta — exportable as Markdown/JSON. The `dual` leaf keeps one report per runtime and shows a comparison card.

## Datasets

Default dataset for **all vector-search variants** (both platforms, every runtime):

- **Enron fraud email dataset** — https://www.kaggle.com/datasets/advaithsrao/enron-fraud-email-dataset?resource=download
- File: `enron_data_fraud_labeled.csv` (~752 MB, tens of thousands of labeled emails). Keep it at the repo root during development — it is **never committed** (`*.csv` is gitignored).
- Each email becomes one raw string — `Folder-Name Date From To Subject Body Cc Bcc`, space-separated, whitespace collapsed — fed into the shared text chunker.
- Apps never bundle the full file: each leaf bundles a small sample (a few hundred rows — see the leaf `README.md` for the extraction command) and exposes an item-count picker on its ingest screen.

## Getting started

Each leaf module is self-contained. Open / build only the variation you want:

**iOS (per variation, Xcode + SwiftPM):**

```bash
cd ios/vector-search/litert   # or onnx, coreml, ../llm/litert-lm, ...
open Package.swift            # or the .xcodeproj once added
```

**Android (per variation, Android Studio + Gradle):**

```bash
cd android/llm/litert-lm      # or onnx-genai, ../vector-search/litert, ...
./gradlew installDebug        # once the Gradle module is added
```

Model files (`*.tflite`, `*.litertlm`, `*.onnx`, `*.mlpackage`) are **not** checked in. Each leaf `README.md` documents where to download the model and where to place it.

## Adding a new variation

1. Create `ios/<area>/<runtime>/` or `android/<area>/<runtime>/` (lowercase, hyphenated, e.g. `tflite`, `onnx-genai`).
2. Copy the closest sibling variation's layout — do not refactor siblings to share code.
3. Include a `README.md` with: runtime version, model + download link, build/run steps, measured latency on a reference device.
4. Update the Structure tree and Technology matrix in this file.
5. Update `AGENTS.md` if you introduce a new convention.

## Conventions

- One variation = one buildable unit (one Xcode project / SPM package, one Gradle module). No cross-runtime dependencies.
- `ios/` is Swift-first (SwiftUI for demos). `android/` is Kotlin-first (Jetpack Compose for demos).
- Keep English docs. Keep sample code small and readable — these are reference implementations, not SDKs.
- See `AGENTS.md` for agent/contributor working rules.

## Roadmap

- [ ] Fill in each leaf module with a runnable sample + `README.md` (runtime version, model link, benchmarks).
- [x] Shared `docs/benchmarks.md` benchmark table (tokens/sec, TTFT, embedding ms, index size, device) — vector-search iOS + Android ONNX/dual, LLM Android + iOS measured; open: Android `vector-search/litert`, ONNX on ANE/Core ML GPU.
- [ ] Add RAG wiring examples combining `vector-search` + `llm` on the same runtime stack.
