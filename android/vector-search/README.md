# Android Vector Search — How It Works

Both leafs (`litert/`, `onnx/`) implement the same pipeline (parity rule in
`AGENTS.md`). Only the embedding runtime differs; chunking, storage, search,
and UI are identical.

## The concept in 30 seconds

Vector search retrieves by **meaning**, not keywords:

1. **Embed** — a neural model maps each text chunk to a dense float vector
   (here: 512 dimensions). Paraphrases land close together; unrelated texts
   land far apart. Vectors are L2-normalized, so closeness = cosine similarity.
2. **Index** — vectors are stored in ObjectBox with an HNSW index (approximate
   nearest-neighbor graph, `COSINE` distance). Indexing is automatic on insert.
3. **Search** — the query is embedded with the *same* model, then ObjectBox
   returns the top-k nearest chunks with distances, converted to `%` scores.

```
corpus text → chunk (blank-line split) → tokenize (WordPiece) → embed (512-d)
    → ObjectBox HNSW index ──→ query → embed → nearestNeighbors(k) → ranked hits
```

## Where it lives (per leaf)

| Step | Code |
|---|---|
| Chunking rules | `domain/Chunking.kt` (`chunkCorpus`) |
| Embedding contract | `domain/Embedder.kt` → `List<Float>`, L2-normalized |
| Runtime (`litert/`) | `data/InterpreterRunner.kt` over raw LiteRT `Interpreter` (CPU), `WordpieceTokenizer`, `LiteRtEmbedderFactory` (assets `models/`) |
| Runtime (`onnx/`) | `MissingModelEmbedder` placeholder — ONNX Runtime wiring lands here next |
| Storage + search | `data/db/DocumentChunk.kt` (`@HnswIndex COSINE`), `data/DocumentRepository.kt` (`ingest`/`stats`/`documentStats`/`search`) |
| Screens | `ui/search/` (query + ranked hits), `ui/ingest/` (corpus prep), `ui/stats/` (report sheet, MD/JSON export) |

## Dependency sizes (measured Sep 2026)

Runtime natives dominate the APK. Numbers are the published AARs plus the
uncompressed `.so` sizes that land per ABI in the final APK.

| Artifact | AAR download | arm64-v8a in APK | x86_64 in APK |
|---|---|---|---|
| LiteRT `litert:2.2.0` (`libLiteRt.so` + GPU `ClGl` plugin) | 11.8 MB | ~8.6 MB | ~11.4 MB |
| LiteRT `litert-api:2.2.0` (`liblitert_jni.so` bridge) | 0.9 MB | ~0.5 MB | ~0.5 MB |
| ONNX Runtime `onnxruntime-android:1.30.0` | 53.0 MB | ~33.1 MB | ~39.5 MB |
| ObjectBox (for scale) | — | ~2.6 MB | ~2.6 MB |
| `bert_embedder.tflite` model (assets, one copy shared by all ABIs) | — | 26.1 MB | same |

Takeaways:

- **ONNX Runtime is ~3–4× heavier per ABI** than LiteRT (~33 MB vs ~9 MB on
  arm64) — full ONNX op coverage vs mobile-focused kernels. It also ships an
  extra `x86` ABI slice LiteRT doesn't have.
- Our debug APK is ~99 MB because it bundles **all ABIs** (incl. emulator
  `x86_64`), debug symbols, and the 26 MB model. A release App Bundle splits
  per ABI, so a real arm64 install is roughly a third of that.
- The embedding model (~26 MB) costs more than the LiteRT runtime itself —
  model choice moves total size more than runtime choice does here.

## Run it

Open one leaf folder in Android Studio (each is a standalone project),
add `app/src/main/assets/models/embedding.tflite` + `vocab.txt` (see the
leaf `README.md` for download links — binaries are never committed), then
`./gradlew installDebug`. Gear icon = ingest corpus, search box = query,
FAB = statistics + MD/JSON export.
