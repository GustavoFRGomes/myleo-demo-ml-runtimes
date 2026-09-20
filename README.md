# MyLeo — On-Device Vector Search + LLM Monorepo

A monorepo of **on-device AI sample implementations** for iOS and Android, covering two problem areas across multiple inference runtimes:

1. **vector-search** — on-device text embeddings + similarity search (the retrieval half of RAG).
2. **llm** — on-device text generation / chat inference (the generation half of RAG).

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
│   │   └── coreml/        # Core ML embedding model + Accelerate search (Swift)
│   └── llm/
│       ├── litert-lm/     # LiteRT-LM (.litertlm) chat inference (Swift)
│       ├── onnx-genai/    # ONNX Runtime GenAI (Phi / Llama / SmolLM) (Swift)
│       └── coreml/        # Core ML / Apple Foundation Models inference (Swift)
└── android/
    ├── vector-search/
    │   ├── litert/        # LiteRT Text Embedder + vector search (Kotlin)
    │   └── onnx/          # ONNX Runtime Mobile embedding model + vector search (Kotlin)
    └── llm/
        ├── litert-lm/     # LiteRT-LM / MediaPipe LLM Inference API (Kotlin)
        └── onnx-genai/    # ONNX Runtime GenAI (Kotlin)
```

> Naming: `litert-lm` and `onnx-genai` are the LLM-specific runtime SDKs (chat/generation).
> Plain `litert` / `onnx` under `vector-search` are the embedding + retrieval SDKs.
> `coreml` exists on iOS only — there is no Core ML on Android.

## Technology matrix

| Area | iOS | Android |
|---|---|---|
| vector-search / LiteRT | `ios/vector-search/litert` — LiteRT embedding (e.g. Universal Sentence Encoder / MiniLM `.tflite`), brute-force cosine or HNSW | `android/vector-search/litert` — LiteRT Text Embedder, same model family |
| vector-search / ONNX | `ios/vector-search/onnx` — ONNX Runtime Mobile + MiniLM/E5 `.onnx` | `android/vector-search/onnx` — ONNX Runtime (AAR) + same `.onnx` |
| vector-search / Core ML | `ios/vector-search/coreml` — Core ML `.mlpackage` embedding + Accelerate/BNNS | — (no Android equivalent) |
| llm / LiteRT-LM | `ios/llm/litert-lm` — LiteRT-LM `.litertlm` bundle (e.g. Gemma), GPU delegate | `android/llm/litert-lm` — LiteRT-LM via MediaPipe `LlmInference` API (Gemma) |
| llm / ONNX GenAI | `ios/llm/onnx-genai` — `onnxruntime-genai` (Obj-C/C++ bridge) running Phi/SmolLM/Llama `.onnx` | `android/llm/onnx-genai` — `onnxruntime-genai` AAR, same model family |
| llm / Core ML | `ios/llm/coreml` — Apple Foundation Models (system-hosted, where available) with Core ML `.mlpackage` fallback | — (no Android equivalent) |

## What each variation implements

### vector-search (`*/vector-search/*`)

Same contract, different runtime:

- Embed a chunk of text → float vector (normalized).
- Store vectors in a small on-device index.
- Query: top-k cosine similarity search with latency + score output.
- Minimal demo UI: ingest sample docs, type a query, see ranked hits.

Each runtime folder owns its model format, tokenizer, and delegate/accelerator setup. Shared logic (e.g. cosine math) must not be force-shared across runtimes — duplication is acceptable if it keeps variations independent.

### llm (`*/llm/*`)

Same contract, different runtime:

- Load a chat-capable model from app bundle / download.
- Stream tokens for a prompt with configurable temperature / top-k / top-p / max tokens.
- Minimal demo UI: system prompt + chat thread + tokens/sec + TTFT display.

Each runtime folder owns its model packaging (`.litertlm`, ONNX GenAI config + tokenizer, Core ML / Foundation Models wiring).

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
- [ ] Add a shared `docs/` benchmark table (tokens/sec, TTFT, embedding ms, index size, device).
- [ ] Add RAG wiring examples combining `vector-search` + `llm` on the same runtime stack.
