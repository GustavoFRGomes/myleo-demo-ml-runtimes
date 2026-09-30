# Benchmarks — Vector Search Runtimes

Reference devices: **Samsung Galaxy S25 Ultra (`SM_S948B`)** for Android,
**iPhone SE 3rd gen** for iOS (Trio leaf, same MiniLM weights in all three files).

## How to reproduce

Every app reports the same three numbers in its stats sheet / logs:

- **Model load ms** — wall time of the runtime's model-open call
  (`Interpreter()` on LiteRT, `ORTSession` on ONNX, `MLModel(contentsOf:)` on Core ML).
  Logged as `MyLeo-Model: embedding model loaded in Xms` (Android) and shown as
  "Embedding model loaded in X ms" (iOS ingest screen).
- **Embed ms / chunk** — tokenize + run + normalize per text chunk, logged per
  chunk (`MyLeo-Ingest: embed chunk i/N (...) in Xms`) and averaged in the ingest summary.
- **Index/search** — ObjectBox HNSW (Android) / brute-force cosine (iOS); reported
  per query but excluded below (index size, not runtime, dominates it).

Method: fresh install → launch → ingest the bundled 300-row Enron sample →
read the stats sheet. `TBD` means not measured yet — fill it in from your own
run rather than guessing.

## iOS (`ios/vector-search/*`)

| Leaf | Runtime (dep size) | Model (size, dims) | Load ms | Embed ms/chunk | Notes |
|---|---|---|---|---|---|
| `litert` | `TensorFlowLiteSwift ~> 2.17` — pod 74 MB all-arch → **~5 MB** installed | MiniLM-L6-v2 `.tflite`, **86 MB**, 384-d | **39** (Trio) | **~53** (Trio: 2633 / 50 mail chunks) | Fastest load: `Interpreter()` mmaps the file, near-zero init — but slowest per-chunk here (Swift `copy` + `invoke` per chunk, CPU) |
| `onnx` | `onnxruntime-objc ~> 1.30` — pod 243 MB all-arch → **~36 MB** installed | MiniLM-L6-v2 `.onnx`, **86 MB**, 384-d | **209** (Trio) | **~28** (Trio: 1377 / 50) | `ORTSession` build parses + optimizes the graph: slower load than mmap, faster steady-state than LiteRT |
| `coreml` | System Core ML — **0 MB** | MiniLM-L6-v2 `.mlpackage`, **86 MB** fp32, 384-d | **452** (Trio) | **~14** (Trio: 710 / 50) | `MLModel(contentsOf:)` compiles the execution plan at load (slowest load); then the fastest per-chunk — optimized CPU path beats both pods here. fp32 chosen deliberately (fp16 degraded this model) |
| `trio` | Both pods above → **~41 MB** installed | All three files, **258 MB** total, 384-d | per-runtime above | per-runtime above | Same weights everywhere; picker lazy-loads one runtime per session |

Debug `.app` totals (measured): litert ~95 MB, onnx ~127 MB, coreml ~91 MB, trio ~310 MB (estimate).

## Android (`android/vector-search/*`)

| Leaf | Runtime (dep size) | Model (size, dims) | Load ms | Embed ms/chunk | Notes |
|---|---|---|---|---|---|
| `litert` | `litert:2.2.0` — AARs ~12 MB download (`litert` 11 MB + `litert-api` 0.9 MB; per-ABI slice installed) | MediaPipe `bert_embedder` `.tflite`, **25 MB**, 512-d | TBD | TBD | Different weights/dims than every other leaf — scores aren't cross-comparable |
| `onnx` | `onnxruntime-android:1.30.0` — AAR **51 MB** download (all ABIs; per-ABI slice installed) | MiniLM-L6-v2 `.onnx`, **86 MB**, 384-d | **417–439** | **~9–19** (350–2630-char emails; 50-mail ingest: ~530 embed of ~660 total) | `OrtSession` from asset bytes; inputs via `LongBuffer` (the generic tensor overload is gone in 1.30) |
| `dual` | Both AARs above, ~63 MB download | MiniLM both formats, **86 + 86 MB**, 384-d shared index | **417** (ONNX path) | **~9–19** (ONNX path) | LiteRT path (MiniLM `.tflite`, named inputs + mean-pool) not yet timed on-device |

## Why one is faster than another

- **Load time is init work, not inference.** LiteRT mmaps a file (fast:
  39 ms iOS / TBD Android). ONNX Runtime parses the graph and builds a session
  with optimizations (209 ms iOS / ~430 ms Android). Core ML additionally
  compiles/optimizes an execution program for the target compute units at
  `MLModel(contentsOf:)` time (452 ms iOS). Rank by load: LiteRT < ONNX <
  Core ML, on both platforms.
- **But load rank ≠ embed rank.** On the iPhone SE per-chunk embed runs
  Core ML (~14 ms) < ONNX (~28 ms) < LiteRT (~53 ms): once compiled, Core ML's
  optimized execution beats both pods' per-invoke overhead. Judge a runtime by
  embed ms for your workload, not by load ms.
- **Embed time is per-token compute on CPU** (no delegates/EPs wired yet in
  any leaf — all CPU). Drivers, in order: sequence length after tokenizing
  (long emails cost more; short queries cost ~10 ms), model width
  (384 vs 512), then runtime overhead (ORT session-run vs TFLite invoke vs
  Core ML prediction). Mean-pooling + L2-normalization in our code is
  negligible next to the model matmuls.
- **Dependency size ≠ installed size.** AARs/pods ship all architectures;
  the installed app keeps one slice (Android per-ABI, iOS app thinning).
  The table lists both where measured.
- **Don't compare scores across leaves with different models.**
  Cosine similarity is only comparable within one weights file —
  `android/vector-search/litert` (BERT 512-d) vs everything else
  (MiniLM 384-d) will rank the same corpus differently by construction.
