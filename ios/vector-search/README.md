# iOS Vector Search — How It Works

Three leafs (`litert/`, `onnx/`, `coreml/`) implement the same pipeline
(parity rule in `AGENTS.md`): corpus → chunks → embeddings → on-device index →
ranked search. Only the embedding runtime differs.

## The concept in 30 seconds

Same retrieval loop as `android/vector-search/` (see its README): text is
embedded to L2-normalized float vectors, stored with their source item, and
ranked by cosine similarity at query time. The demo ingest screen supports
free text plus the shared Enron sample (see Datasets in the root `README.md`).

## Reuse strategy: copy, don't share

Per the monorepo rules there is **no shared framework** between leafs. Code
reuse across runtimes means **copying files**, never cross-imports. The
following are byte-identical apart from mechanical renames (module import,
`Logger` subsystem, display strings) — keep them in sync when they change:

- Text pipeline: `Domain/Chunking.swift` (`chunkCorpus` — blank-line split),
  `Domain/CSVParser.swift` (RFC-4180 reader), `Domain/EnronDataset.swift`
  (email → raw string: `Folder-Name Date From To Subject Body Cc Bcc`,
  whitespace-collapsed; `BundleEnronDatasetLoader`).
- Embedding contract: `Domain/Embedder.swift`, `Domain/TextTokenizer.swift`,
  `Domain/CalculateEmbeddingUseCase.swift`, `Data/EmbeddingRepository.swift`,
  `Data/WordpieceTokenizer.swift`, `Data/LiteRTEmbedder.swift` (runtime seam +
  L2 norm), `Data/MissingModelEmbedder.swift`, `Data/IOSSystemStatsProvider.swift`.
- Storage + search: `Data/DocumentRepository.swift` (in-memory cosine index
  behind the protocol; a persistent index plugs in per runtime next).
- UI (MVI): `UI/Search/`, `UI/Ingest/` (source picker + item count),
  `UI/Stats/` (report sheet, MD/JSON export), `UI/ResultDetail/` (email view
  with query highlighting), `UI/AppRootView.swift`.
- Reports: `Domain/StatsReport.swift` (MD/JSON builders), `Data/StatsExporter.swift`.
- Tests mirror the sources one-to-one (`Tests/`).

## Per-runtime differences (the only files that differ by design)

| Leaf | Runtime wiring |
|---|---|
| `litert/` | CocoaPods `TensorFlowLiteSwift` (`Podfile` + `Podfile.lock` committed); `Data/InterpreterRunner.swift` over the raw `Interpreter` API (inputs located by tensor name, sequence out + mean pooling); `Data/LiteRTEmbedderFactory.swift` loads `embedding.tflite` + `vocab.txt` from the bundle (384-d MiniLM, same weights as the other leafs) |
| `onnx/` | CocoaPods `onnxruntime-objc` + bridging header; `Data/OnnxRunner.swift` over `ORTSession` (mean pooling); `Data/OnnxEmbedderFactory.swift` loads `embedding.onnx` + `vocab.txt` (384-d) |
| `coreml/` | No pods — Apple `CoreML` framework; `Data/CoreMLRunner.swift` over `MLModel` (mean pooling, CPU); `Data/CoreMLEmbedderFactory.swift` loads `embedding.mlpackage` + `vocab.txt` (384-d) |

## Build/run (per leaf)

```bash
cd ios/vector-search/litert   # or onnx, coreml
xcodegen generate             # after any project.yml / file change
pod install                   # litert + onnx only (regenerating wipes pod patches: xcodegen first, pod last)
open MyLeoLite.xcworkspace    # always the workspace where pods exist, else the .xcodeproj
```

Device runs need signing overrides (never committed):
`xcodebuild -workspace … -scheme … -destination 'platform=iOS,id=<UDID>' test DEVELOPMENT_TEAM=<TEAM> CODE_SIGN_STYLE=Automatic -allowProvisioningUpdates`.

## Models & datasets (never committed)

Place these under each leaf's `Sources/Resources/` (gitignored — the app
falls back to its missing-model error state without them):

- `litert/`: `embedding.tflite` ← MiniLM converted from `sentence-transformers/all-MiniLM-L6-v2` (https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2, Apache-2.0, ~90 MB, 384-d) via `TFAutoModel` + `TFLiteConverter` (built-ins only). Same weights as the other leafs. `vocab.txt` ← https://huggingface.co/bert-base-uncased/resolve/main/vocab.txt.
- `onnx/`: `embedding.onnx` ← https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx (`all-MiniLM-L6-v2`, Apache-2.0, ~90 MB, 384-d). `vocab.txt` ← https://huggingface.co/bert-base-uncased/resolve/main/vocab.txt (byte-identical to the BERT vocab).
- `coreml/`: `embedding.mlpackage` ← convert `sentence-transformers/all-MiniLM-L6-v2` (https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2, Apache-2.0) with `coremltools` (explicit `position_ids` input, `compute_precision=FLOAT32`, iOS 17 target; ~86 MB, 384-d — see the leaf README for the recipe). `vocab.txt` ← same BERT vocab link as `onnx/`.
- All leafs: `enron_sample.csv` ← 300-row extract of the default dataset (see Datasets in the root `README.md`; extraction command in the litert leaf README).
