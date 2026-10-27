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
android/llm/dual/              # Android chat inference dual runtime (LiteRT-LM + ONNX GenAI)
android/decision/litert/       # Android structured decisions (Julia-1) with LiteRT
android/decision/onnx/         # Android structured decisions (Julia-1) with ONNX Runtime
android/decision/dual/         # Android structured decisions dual runtime (LiteRT + ONNX)
```

`decision/` exists on Android only for now (no iOS leaf). `coreml/` exists on iOS only. Never create `android/**/coreml/`. Android has no Core ML equivalent.

Naming is exact: `litert` = single-pass runtime (embedding/retrieval or classification), `litert-lm` = LLM generation runtime, `onnx` = single-pass ONNX runtime, `onnx-genai` = LLM generation. Do not rename or merge them (e.g. never collapse `litert` + `litert-lm` into one folder).

## What belongs where

- `ios/` — Swift-first only. SwiftUI for any future demo UI. One variation = one future Xcode project / SPM package. No Podfiles, no Obj-C unless required as a thin bridge (only in `ios/llm/onnx-genai`).
- `android/` — Kotlin-first only. Jetpack Compose for any future demo UI. Each leaf folder is one **standalone Gradle project** cloned from the Activity + Compose template (see Android section below): open the leaf folder itself in Android Studio, not an `android/` root. No Java unless required for interop.
- `*/vector-search/*` — embedding + index + top-k cosine search contract only. No chat UI, no token streaming, no generation params.
- `*/llm/*` — prompt → streaming token contract only (temperature, top-k, top-p, max-tokens, tokens/sec + TTFT reporting). No embedding index, no retrieval logic.
- `*/decision/*` — one forward pass per request: state + typed question (`choice` / `score` / `noul`) + 2–20 options → one probability per option. No token streaming, no chat UI, no retrieval index.
- Model binaries (`*.tflite`, `*.litertlm`, `*.onnx`, `*.mlpackage`, `*.bin`) are never committed. Each leaf `README.md` must document download URL + placement path instead.
- Root `README.md` = structure tree + technology matrix (keep in sync when adding folders). Leaf `README.md` = runtime version, model, scope contract for that variation. This `AGENTS.md` = working rules.

## Rules (must follow)

1. **iOS projects are XcodeGen-generated — do not hand-edit `.xcodeproj`.** Each `ios/*/*/` leaf is a real project defined by `project.yml` (+ `Info.plist`, `Sources/`, `Tests/`). Regenerate with `xcodegen generate` after changing the spec; never commit `*.xcodeproj/` (gitignored). Do not create `Package.swift`, `*.podspec`, or CI workflows unless explicitly asked.
2. **Keep variations independent.** Never add imports / dependencies between leaf folders. Never extract shared code across runtimes. Small duplication is preferred over coupling.
3. **Follow existing markdown style.** Short headers, concrete file paths in backticks, no placeholders like `TODO` or `coming soon` without a one-line description of what goes there.
4. **Lowercase hyphenated folder names only** (`onnx-genai`, not `onnx_genai` or `OnnxGenAI`). One concept per folder — do not nest deeper than `platform/area/runtime/` without asking.
5. **No model weights, no datasets, no keys.** If a doc needs a model, link it and state license + size. Never paste API keys or provisioning profiles.
6. **Verify before finishing.** Run `find ios android -type d | sort` (or equivalent listing) and confirm the tree matches the Repo map above. Read back any markdown you write.

## Architecture & patterns (must follow, once code is requested)

- **iOS = MVI, Android = MVVM. No exceptions without asking.** `ios/*` uses Model–View–Intent (unidirectional intent → state); `android/*` uses Model–View–ViewModel (ViewModel + unidirectional UI state via StateFlow).
- **Prefer boring, standard patterns.** Use platform-idiomatic solutions first: Repository for data/inference access, DI via constructor injection, Strategy/Adapter for runtime delegates, Observer/Flow for streaming tokens. Do not invent custom architectures or introduce exotic frameworks (TCA, Redux variants, MVI on Android, MVVM on iOS) without asking.
- Keep inference engines behind a narrow interface (e.g. `Embedder` / `ChatInference`) so the runtime swap stays inside one leaf folder. UI talks to ViewModel/Intent layer only, never to the SDK directly.

## iOS (XcodeGen projects — applies now)

Reference template: `ios/vector-search/litert/` (`MyLeoLite`) — minimal SwiftUI app
generated from `project.yml` via XcodeGen (`brew install xcodegen`). All other
iOS leafs are clones of it with identifiers renamed.

- Each `ios/*/*/` leaf is a **standalone Xcode project**: its own `project.yml`,
  `Info.plist`, `Sources/` (SwiftUI `@main` App + views), and `Tests/` (XCTest).
  There is no `ios/` workspace. Open the leaf's generated `.xcodeproj` in Xcode.
- Template settings (keep clones in sync unless asked): iOS deployment target
  `17.0`, Swift `6.0`, SwiftUI lifecycle, one unit-test target per leaf.
- Workflow per leaf: `xcodegen generate`, then `open *.xcodeproj` or
  `xcodebuild -scheme <Target> -destination 'platform=iOS Simulator,name=iPhone 17' build`
  (unit tests: same command with `test`; needs a matching simulator runtime).
- Never hand-edit generated `*.xcodeproj/` — change `project.yml` and regenerate.
- CocoaPods (LiteRT + onnx leafs only — no SPM exists for either runtime): `Podfile` +
  `Podfile.lock` are committed; `Pods/` + `*.xcworkspace` are gitignored and
  regenerate via `pod install`. Always build the workspace, never the bare
  `.xcodeproj`. Order matters: `xcodegen generate` first, `pod install` last
  (regenerating wipes CocoaPods' project patches). The `post_install`
  deployment-target bump + `ENABLE_USER_SCRIPT_SANDBOXING=NO` in `project.yml`
  are required (pods target old iOS; Xcode 26 script sandboxing). The onnx leaf
  additionally needs a bridging header (`SWIFT_OBJC_BRIDGING_HEADER`) for the
  ObjC runtime API. Never commit
  personal signing (`DEVELOPMENT_TEAM`, profiles) — pass as `xcodebuild`
  overrides for device runs only.
- Package / naming convention:

| Leaf | Project / target / scheme | Bundle ID | Display name |
|---|---|---|---|
| `ios/vector-search/litert/` (reference) | `MyLeoLite` | `org.ggomes.myleolite` | `MyLeo Lite` |
| `ios/vector-search/onnx/` | `MyLeoVSOnnx` | `org.ggomes.myleo.vectorsearch.onnx` | `MyLeo VS ONNX` |
| `ios/vector-search/coreml/` | `MyLeoVSCoreML` | `org.ggomes.myleo.vectorsearch.coreml` | `MyLeo VS Core ML` |
| `ios/vector-search/trio/` | `MyLeoVSTrio` | `org.ggomes.myleo.vectorsearch.trio` | `MyLeo Trio` |
| `ios/llm/litert-lm/` | `MyLeoLLMLiteRTLM` | `org.ggomes.myleo.llm.litertlm` | `MyLeo LLM LiteRT-LM` |
| `ios/llm/onnx-genai/` | `MyLeoLLMOnnxGenAI` | `org.ggomes.myleo.llm.onnxgenai` | `MyLeo LLM ONNX-GenAI` |
| `ios/llm/coreml/` | `MyLeoLLMCoreML` | `org.ggomes.myleo.llm.coreml` | `MyLeo LLM Core ML` |
| `ios/llm/trio/` | `MyLeoLLMTrio` | `org.ggomes.myleo.llm.trio` | `MyLeo LLM Trio` |
| `ios/decision/litert/` | `MyLeoDecisionLiteRT` | `org.ggomes.myleo.decision.litert` | `MyLeo Decision LiteRT` |
| `ios/decision/onnx/` | `MyLeoDecisionOnnx` | `org.ggomes.myleo.decision.onnx` | `MyLeo Decision ONNX` |
| `ios/decision/trio/` | `MyLeoDecisionTrio` | `org.ggomes.myleo.decision.trio` | `MyLeo Decision Trio` |

- When cloning the template to a new iOS variant: copy `project.yml`,
  `Info.plist`, `Sources/`, `Tests/`. **Never copy** `*.xcodeproj/`, `build/`,
  or `DerivedData`. Preserve the target's existing `README.md`. Then rename:
  project/target/scheme + test target in `project.yml`, `PRODUCT_BUNDLE_IDENTIFIER`,
  `CFBundleDisplayName` in `Info.plist`, the `@main` App struct + file name,
  greeting text in `ContentView.swift`, and the test class + `@testable import`
  in `Tests/`.
- iOS MVI: `Store` (`ObservableObject`, `@Published` state, `send(Intent)`)
  per screen; SwiftUI views render state and send intents only.
- iOS tests: `XCTest` in each leaf's `Tests/` target. Every clone must keep its
  package-correct example test compiling.

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
| `android/llm/dual/` | `org.ggomes.myleo.llm.dual` | `MyLeo LLM Dual` | `MyLeoLlmDualTheme` | `Theme.MyLeoLlmDual` | `MyLeo LLM Dual` |
| `android/vector-search/dual/` | `org.ggomes.myleo.vectorsearch.dual` | `MyLeo VS Dual` | `MyLeoVsDualTheme` | `Theme.MyLeoVsDual` | `MyLeo VS Dual` |
| `android/decision/litert/` | `org.ggomes.myleo.decision.litert` | `MyLeo Decision LiteRT` | `MyLeoDecisionLitertTheme` | `Theme.MyLeoDecisionLitert` | `MyLeo Decision LiteRT` |
| `android/decision/onnx/` | `org.ggomes.myleo.decision.onnx` | `MyLeo Decision ONNX` | `MyLeoDecisionOnnxTheme` | `Theme.MyLeoDecisionOnnx` | `MyLeo Decision ONNX` |
| `android/decision/dual/` | `org.ggomes.myleo.decision.dual` | `MyLeo Decision Dual` | `MyLeoDecisionDualTheme` | `Theme.MyLeoDecisionDual` | `MyLeo Decision Dual` |

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

### Decision — every `*/decision/*` leaf (Android `litert`/`onnx`/`dual`)

- F-DEC-1: Request = state text + question type (`choice` / `score` / `noul`) + 2–20 ordered options; strict encoding (reject over-long requests, never truncate).
- F-DEC-2: Tokenize, build the marker sequence (`<mask>` before each option), gather host-side embeddings where the runtime requires it, run **one** forward pass; probabilities = softmax over the marker logits at temperature 1.
- F-DEC-3: Answer in the author's terms: `choice` = winning option id, `score` = expected zero-based rubric index, `noul` = P(true).
- F-DEC-4: Report model load ms (tokenizer / table / graph) + per-request prepare, graph and decode ms, token count, window and accelerator.
- F-DEC-5: UI: state field, question/type/options editor, presets, probability bars per option, error/empty states. Android = Compose.
- F-DEC-6: Suite mode: the same 18 built-in prompts (8 multi-answer, 5 score, 5 yes/no) run in order on the active runtime, with per-prompt progress/cancel and results shown as they arrive.
- F-DEC-7: Benchmark report per suite run: per-prompt and per-type timings (total/average/min/max), wall clock, load timings, device profile, battery level + charge-counter delta (null when unavailable), exportable as Markdown and JSON. A `dual` leaf keeps one report per runtime and offers a comparison.
- Accelerator rule: GPU must request **FP32** explicitly; if the device cannot compile it, fall back to CPU and report which backend ran. No fp16 and no NPU path (fp16 changes the answers).

Parity notes: Julia-1 ships per runtime (`.tflite` + host token table for LiteRT, `.onnx` with the table in-graph for ONNX); all decision leaves implement F-DEC-1…7 and, with the same tokenizer, accept identical token ids for the same request. Accelerated backends differ by runtime and must be documented: LiteRT runs GPU FP32 with CPU fallback, ONNX is CPU-only (the published export targets browser WebGPU and no Android EP is validated). A `dual` leaf adds one `RuntimeBinding` per runtime (locate/download/build) and must swap engines on selection instead of keeping both models resident, while preserving the edited request for comparison.

## Testing (must follow, once code is requested)

- **Every feature/bugfix ships with at least unit tests.** No test-less code, even for samples.
- iOS: `XCTest` (+ `swift-testing` only if already used in that leaf). Android: JUnit + Mockito + AssertJ (Turbine/Turbine-style flow assertions where flows are involved).
- Prioritize pure-logic tests: cosine/top-k ranking, chunking, prompt building, generation params mapping, MVI reducer / ViewModel state transitions. Mock the inference SDK — never require a downloaded model to run unit tests.
- Name tests `subject_condition_expectation` and keep one behavior per test.

## User confirmation gate (must follow)

- **Never commit before the user confirms.** No `git commit`, no `git push`, no PR creation/merging unless the user explicitly said to for that exact feature/bugfix.
- **Request validation per feature/bugfix.** After implementing, stop and ask: summarize what changed, how to verify (commands / manual steps), and test results. Wait for explicit approval before committing or moving to the next feature.
- One feature/bugfix = one validation round. Do not batch unrelated changes into a single confirmation.

## Parked work (patches)

Experiments that are not ready to land stay as patch files under `patches/` and are
committed for continuity. They are not built by any module, are not covered by the area
contracts, and must not be applied without a validation round.

- `patches/android-vector-search-dual-litert-gpu.patch` — LiteRT GPU attempt for
  `android/vector-search/dual`: `CompiledModel` + `GpuOptions` (FP16), a
  `--es litert_ep gpu` switch with warmup validation and CPU fallback, a
  `--es bench_runtime litert` option for the sustained power loop, and
  `LiteRtExecutionProvider` tests. Blocked by the model, not the device: the MiniLM
  `.tflite` export uses `GATHER` with 2-D indices, which the GPU delegate rejects
  (`GATHER: Only support 1D indices`), leaving two partitions that `CompiledModel`
  refuses to compile (`Failed to compile model`). Resume after re-exporting the model
  with 1-D gather indices.
  Apply with `git apply patches/android-vector-search-dual-litert-gpu.patch`.


## Benchmarks dashboard (must stay in sync)

`docs/benchmarks.html` is the interactive view of every benchmark in `docs/benchmarks*.md`.
Whenever a benchmark is documented — a new leaf, a new runtime, a new measurement, or a
corrected number — update the dashboard in the **same change**:

1. append or replace the row in the `<script id="bench-data" type="application/json">` block of
   `docs/benchmarks.html` (one object per measured row, device included);
2. regenerate `docs/benchmarks-data.csv` from that block;
3. re-render `docs/benchmarks-dashboard.png` (headless Chrome);
4. regenerate `docs/standalone-benchmarks.html` with
   `python3 docs/tools/standalone_benchmarks.py` (script-free single-file snapshot of the same
   rows, used when the page must travel without its CSV/PNG siblings).

Commands and the CSV snippet live in `docs/benchmarks-dashboard.md` → *Update the numbers*.
A benchmark change that leaves the dashboard stale is incomplete.

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
