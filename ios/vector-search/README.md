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
| `litert/` | CocoaPods `TensorFlowLiteSwift` (`Podfile` + `Podfile.lock` committed); `Data/InterpreterRunner.swift` over the raw `Interpreter` API; `Data/LiteRTEmbedderFactory.swift` loads `embedding.tflite` + `vocab.txt` from the bundle |
| `onnx/` | `MissingModelEmbedder` placeholder; no runtime dependency yet |
| `coreml/` | `MissingModelEmbedder` placeholder; no runtime dependency yet |

## Build/run (per leaf)

```bash
cd ios/vector-search/litert   # or onnx, coreml
xcodegen generate             # after any project.yml / file change
pod install                   # litert only (regenerating wipes pod patches: xcodegen first, pod last)
open MyLeoLite.xcworkspace    # always the workspace where pods exist, else the .xcodeproj
```

Device runs need signing overrides (never committed):
`xcodebuild -workspace … -scheme … -destination 'platform=iOS,id=<UDID>' test DEVELOPMENT_TEAM=<TEAM> CODE_SIGN_STYLE=Automatic -allowProvisioningUpdates`.

## Models & datasets (never committed)

- `Sources/Resources/embedding.tflite` + `vocab.txt` (litert only): MediaPipe
  `bert_embedder`, see the leaf README for links.
- `Sources/Resources/enron_sample.csv`: 300-row extract of the default
  dataset — regenerate with the command in the leaf README.
