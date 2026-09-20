# AGENTS.md — Working Rules for AI Agents and Contributors

This repo is a **monorepo of independent on-device AI reference implementations**. No shared framework, no cross-module dependencies. Each leaf folder is one self-contained variation.

## Repo map (authoritative)

```text
ios/vector-search/litert/    # iOS vector search with LiteRT embedding
ios/vector-search/onnx/      # iOS vector search with ONNX Runtime Mobile
ios/vector-search/coreml/    # iOS vector search with Core ML
ios/llm/litert-lm/           # iOS chat inference with LiteRT-LM (.litertlm)
ios/llm/onnx-genai/          # iOS chat inference with ONNX Runtime GenAI
ios/llm/coreml/              # iOS chat inference with Core ML / Foundation Models
android/vector-search/litert/  # Android vector search with LiteRT
android/vector-search/onnx/    # Android vector search with ONNX Runtime Mobile
android/llm/litert-lm/         # Android chat inference with LiteRT-LM (MediaPipe LlmInference)
android/llm/onnx-genai/        # Android chat inference with ONNX Runtime GenAI
```

`coreml/` exists on iOS only. Never create `android/**/coreml/`. Android has no Core ML equivalent.

Naming is exact: `litert` = embedding/retrieval runtime, `litert-lm` = LLM generation runtime, `onnx` = embedding/retrieval, `onnx-genai` = LLM generation. Do not rename or merge them (e.g. never collapse `litert` + `litert-lm` into one folder).

## What belongs where

- `ios/` — Swift-first only. SwiftUI for any future demo UI. One variation = one future Xcode project / SPM package. No Podfiles, no Obj-C unless required as a thin bridge (only in `ios/llm/onnx-genai`).
- `android/` — Kotlin-first only. Jetpack Compose for any future demo UI. Each leaf folder is one **standalone Gradle project** cloned from the Activity + Compose template (see Android section below): open the leaf folder itself in Android Studio, not an `android/` root. No Java unless required for interop.
- `*/vector-search/*` — embedding + index + top-k cosine search contract only. No chat UI, no token streaming, no generation params.
- `*/llm/*` — prompt → streaming token contract only (temperature, top-k, top-p, max-tokens, tokens/sec + TTFT reporting). No embedding index, no retrieval logic.
- Model binaries (`*.tflite`, `*.litertlm`, `*.onnx`, `*.mlpackage`, `*.bin`) are never committed. Each leaf `README.md` must document download URL + placement path instead.
- Root `README.md` = structure tree + technology matrix (keep in sync when adding folders). Leaf `README.md` = runtime version, model, scope contract for that variation. This `AGENTS.md` = working rules.

## Rules (must follow)

1. **iOS is structure only for now — do not scaffold iOS projects.** Do not create `.xcodeproj`, `Package.swift`, `*.podspec`, `*.swift` source files, or CI workflows unless explicitly asked. iOS folders + markdown context only. **Android leafs are real projects** (see Android section) — cloning the template is expected when asked.
2. **Keep variations independent.** Never add imports / dependencies between leaf folders. Never extract shared code across runtimes. Small duplication is preferred over coupling.
3. **Follow existing markdown style.** Short headers, concrete file paths in backticks, no placeholders like `TODO` or `coming soon` without a one-line description of what goes there.
4. **Lowercase hyphenated folder names only** (`onnx-genai`, not `onnx_genai` or `OnnxGenAI`). One concept per folder — do not nest deeper than `platform/area/runtime/` without asking.
5. **No model weights, no datasets, no keys.** If a doc needs a model, link it and state license + size. Never paste API keys or provisioning profiles.
6. **Verify before finishing.** Run `find ios android -type d | sort` (or equivalent listing) and confirm the tree matches the Repo map above. Read back any markdown you write.

## Architecture & patterns (must follow, once code is requested)

- **iOS = MVI, Android = MVVM. No exceptions without asking.** `ios/*` uses Model–View–Intent (unidirectional intent → state); `android/*` uses Model–View–ViewModel (ViewModel + unidirectional UI state via StateFlow).
- **Prefer boring, standard patterns.** Use platform-idiomatic solutions first: Repository for data/inference access, DI via constructor injection, Strategy/Adapter for runtime delegates, Observer/Flow for streaming tokens. Do not invent custom architectures or introduce exotic frameworks (TCA, Redux variants, MVI on Android, MVVM on iOS) without asking.
- Keep inference engines behind a narrow interface (e.g. `Embedder` / `ChatInference`) so the runtime swap stays inside one leaf folder. UI talks to ViewModel/Intent layer only, never to the SDK directly.

## Android (standalone Gradle projects — applies now)

Reference template: `android/vector-search/litert/` — Android Studio **Activity + Compose** template as created by the user. All other Android leafs are clones of it with identifiers renamed.

- Each `android/*/*/` leaf is a **standalone project**: its own `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/`, `gradlew`, and `app/` module. There is no `android/` root project. Open the leaf folder itself in Android Studio.
- Template versions (from `gradle/libs.versions.toml` in the reference — keep clones in sync unless asked): AGP `9.5.0-alpha06`, Kotlin `2.4.10`, Compose for Android via Compose BOM `2026.02.01` (Material3 + `activity-compose`), lifecycle `2.6.1` (`lifecycle-viewmodel-ktx` + `lifecycle-viewmodel-compose` for MVVM screens), `kotlinx-coroutines-test` `1.11.0`, ObjectBox `5.4.2`, `mockito-core` `5.23.0`, `mockito-kotlin` `6.3.0`, `assertj-core` `3.27.7`, `compileSdk`/`targetSdk` `37`, `minSdk` `29`, Java `11`. New/updated Android code targets Kotlin `2.4.10` — do not downgrade to older Kotlin without asking.
- UI stack is Compose for Android (Material3, `activity-compose`, Compose BOM). No Views/XML layouts for new screens unless explicitly asked.
- Vector search persistence + index is ObjectBox. Both `android/vector-search/*` leafs must store documents + embeddings in ObjectBox and run top-k vector search through ObjectBox (HNSW / vector-search queries) — not brute-force in memory, not Room/SQLite, not a custom file format — unless the user explicitly approves an alternative.
- ObjectBox entity convention: one `@Entity` (`DocumentChunk`: `document` label + `text` + `@HnswIndex(dimensions = EMBEDDING_DIMENSIONS, distanceType = VectorDistanceType.COSINE)` `FloatArray` embedding, `EMBEDDING_DIMENSIONS` = model width as `Long` const), store via a `BoxStore` singleton (`androidContext` + fixed db name), access via a `DocumentRepository` (`ingest` appends one labeled document per run; `clear` wipes; `stats` + `documentStats` return totals and per-document chunk counts; `search` uses `nearestNeighbors(...).findWithScores()` and converts COSINE distance to similarity). Unit-test with a real JVM `BoxStore` (`TemporaryFolder`); vectors in tests must be exactly `EMBEDDING_DIMENSIONS`-wide.
- Stats sheet/export convention: FAB on the search screen opens a bottom sheet fed by `StatsReportData` (model name + load ms, device manufacturer/model + battery, corpus totals + per-document counts, last ingest run with battery start→end, last search); pure `buildStatsMarkdown`/`buildStatsJson` builders (unit-tested, incl. JSON escaping) + a device-only MediaStore exporter (Downloads, no permissions on API 29+).
- Dependency split: `android/vector-search/*` leafs apply the ObjectBox Gradle plugin (`io.objectbox`), `com.android.legacy-kapt` (required for ObjectBox with AGP 9+), `implementation(io.objectbox:objectbox-kotlin)`, plus `testImplementation` for JUnit + `mockito-core` + `mockito-kotlin` + `assertj-core`. `android/llm/*` leafs take only the test stack (JUnit + Mockito + AssertJ) — no ObjectBox, no kapt. All versions come from `gradle/libs.versions.toml`.
- Configuration cache: `org.gradle.configuration-cache=true` everywhere **except** `android/vector-search/*` leafs, which set it to `false` with an explanatory comment — the ObjectBox Gradle plugin (`PrepareTask`) cannot be serialized into the configuration cache. Do not re-enable CC in ObjectBox leafs without verifying a clean `:app:assembleDebug` first.
- Package / naming convention (`namespace` = `applicationId` = Kotlin package = instrumented-test assertion):

| Leaf | namespace / applicationId | App name | Compose theme fn | Style | `rootProject.name` |
|---|---|---|---|---|---|
| `android/vector-search/litert/` (reference) | `org.ggomes.myleolite` | `MyLeo Lite` | `MyLeoLiteTheme` | `Theme.MyLeoLite` | `MyLeo Lite` |
| `android/vector-search/onnx/` | `org.ggomes.myleo.vectorsearch.onnx` | `MyLeo VS ONNX` | `MyLeoVsOnnxTheme` | `Theme.MyLeoVsOnnx` | `MyLeo VS ONNX` |
| `android/llm/litert-lm/` | `org.ggomes.myleo.llm.litertlm` | `MyLeo LLM LiteRT-LM` | `MyLeoLlmLitertlmTheme` | `Theme.MyLeoLlmLitertlm` | `MyLeo LLM LiteRT-LM` |
| `android/llm/onnx-genai/` | `org.ggomes.myleo.llm.onnxgenai` | `MyLeo LLM ONNX-GenAI` | `MyLeoLlmOnnxgenaiTheme` | `Theme.MyLeoLlmOnnxgenai` | `MyLeo LLM ONNX-GenAI` |

- When cloning the template to a new Android variant: copy `.gitignore`, `app/`, `build.gradle.kts`, `gradle.properties`, `gradle/`, `gradlew`, `gradlew.bat`, `settings.gradle.kts`. **Never copy** `.gradle/`, `.idea/`, `build/`, or `local.properties`. Preserve the target's existing `README.md`. Then move the three `org/ggomes/myleolite` source roots (`app/src/main`, `app/src/androidTest`, `app/src/test`) to the new package path and replace: package + imports in all `*.kt`, `namespace` + `applicationId` in `app/build.gradle.kts`, `rootProject.name` in `settings.gradle.kts`, `app_name` in `res/values/strings.xml`, style in `res/values/themes.xml` + `AndroidManifest.xml` (both `application` and `activity` theme attrs), theme composable fn name, and the `assertEquals(<package>)` in `ExampleInstrumentedTest.kt`.
- Android MVVM: keep `MainActivity` thin (edge-to-edge + `setContent` only). Screen state lives in a `ViewModel` exposed as `StateFlow`; inference SDK access goes through a `Repository` interface. Compose screens observe state only.
- Android tests: unit tests stay in `app/src/test/` and use JUnit + Mockito + AssertJ (mock the runtime SDK / ObjectBox where needed); instrumented tests in `app/src/androidTest/`. Every clone must keep its package-correct `ExampleUnitTest` + `ExampleInstrumentedTest` compiling. `testOptions.unitTests.isReturnDefaultValues = true` is set so `android.util.Log` (used for perf logs) no-ops on the JVM.

## Feature contracts & parity (must follow)

Same area = same features everywhere. Only the runtime SDK, model format, and delegate/accelerator wiring differ per leaf. Do not ship a feature in one leaf of an area without shipping (or explicitly deferring with user approval) the same feature in all other leafs of that area.

### Vector search — every `*/vector-search/*` leaf (iOS `litert`/`onnx`/`coreml`, Android `litert`/`onnx`)

- F-VS-1: Ingest sample documents + chunk text (same chunking rules everywhere).
- F-VS-2: Embed via the leaf's runtime SDK → normalized float vector.
- F-VS-3: Persist documents + vectors (Android: ObjectBox; iOS: runtime-idiomatic store, e.g. Core ML + Accelerate/BNNS-compatible index).
- F-VS-4: Top-k cosine-similarity search (Android via ObjectBox vector search/HNSW) returning ranked hits with scores.
- F-VS-5: Report embed ms + search ms per query.
- F-VS-6: Demo UI: ingest action, query field, ranked-hit list with scores. iOS = SwiftUI, Android = Compose.

### LLM — every `*/llm/*` leaf (iOS `litert-lm`/`onnx-genai`/`coreml`, Android `litert-lm`/`onnx-genai`)

- F-LLM-1: Load chat model from bundle/assets/download path; show model id + load state + errors.
- F-LLM-2: Prompt → streaming tokens with temperature, top-k, top-p, max-tokens (expose only params the runtime supports; document gaps in the leaf `README.md`).
- F-LLM-3: Cancel/stop generation, plus error/empty states.
- F-LLM-4: Report TTFT + tokens/sec per generation.
- F-LLM-5: Chat UI: system prompt field, message thread with streaming updates, regenerate/clear. iOS = SwiftUI, Android = Compose.

Parity notes: `coreml` leafs are iOS-only but must still implement the full area contract. Runtime-only extras (e.g. a delegate toggle, NNAPI/GPU selector) are allowed but must be documented in that leaf's `README.md` and must not remove contract features.

## Testing (must follow, once code is requested)

- **Every feature/bugfix ships with at least unit tests.** No test-less code, even for samples.
- iOS: `XCTest` (+ `swift-testing` only if already used in that leaf). Android: JUnit + Mockito + AssertJ (Turbine/Turbine-style flow assertions where flows are involved).
- Prioritize pure-logic tests: cosine/top-k ranking, chunking, prompt building, generation params mapping, MVI reducer / ViewModel state transitions. Mock the inference SDK — never require a downloaded model to run unit tests.
- Name tests `subject_condition_expectation` and keep one behavior per test.

## User confirmation gate (must follow)

- **Never commit before the user confirms.** No `git commit`, no `git push`, no PR creation/merging unless the user explicitly said to for that exact feature/bugfix.
- **Request validation per feature/bugfix.** After implementing, stop and ask: summarize what changed, how to verify (commands / manual steps), and test results. Wait for explicit approval before committing or moving to the next feature.
- One feature/bugfix = one validation round. Do not batch unrelated changes into a single confirmation.

## Adding a new variation (when asked)

1. Create exactly one folder: `<platform>/<area>/<runtime>/` (e.g. `android/vector-search/coreml` is invalid — stop and ask).
2. Add exactly one `README.md` inside it following the leaf template below. Nothing else.
3. Update root `README.md` Structure tree + Technology matrix.
4. Do not touch sibling variations.

Leaf `README.md` template (keep under ~30 lines):

```markdown
# <Platform> <Area> — <Runtime>

- Runtime: <SDK + version, e.g. LiteRT-LM 0.4 / onnxruntime-genai 0.5>
- Model format: <e.g. `.litertlm`, `.onnx` + tokenizer>
- Scope contract: <3–5 bullets of what this variation will implement>
- Model: <name + download link + license + size, placement path>
- Future build/run: <one-liner, e.g. open Xcode / `./gradlew installDebug`>
```

## Preferred answers to common ambiguities

- "Where does RAG glue code go?" — Nowhere yet. Retrieval and generation stay separate. A future `docs/` or `samples/` RAG example may combine them; do not add one unasked.
- "LiteRT-LM vs LiteRT-ML naming?" — Canonical name is `litert-lm` everywhere (both platforms). `LiteRT-ML` in old messages means `LiteRT-LM`.
- "Should iOS LLM Core ML use Foundation Models?" — Yes: system-hosted Foundation Models where available, Core ML `.mlpackage` fallback. Document both in that leaf README.
