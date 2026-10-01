# Benchmarks — Vector Search + LLM Runtimes

Reference devices: **Samsung Galaxy S25 Ultra (`SM_S948B`)** for Android,
**iPhone SE 3rd gen** for iOS (vector-search Trio leaf, same MiniLM weights in all
three files; LLM leaves differ per platform — see the LLM section).

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
- **LLM: load ms / TTFT / tok/s** — the chat banner shows model load ms; each
  assistant message reports TTFT + tokens/sec. Tokens/sec is over the **decode
  window only** (`(total − ttft)`), so it excludes prompt processing.

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

## Accelerated runs — iOS ANE (separate from the CPU baselines above)

Measured on the Trio app on the iPhone SE 3rd gen, 50 Enron email chunks,
with ANE acceleration requested on all three runtimes. Same models, same
dims — only the execution path changes:

| Runtime (Trio) | Accelerator | How | Load ms | Ingest total ms | Embed ms (50 chunks) | Embed ms/chunk |
|---|---|---|---|---|---|---|
| LiteRT | ANE via `CoreMLDelegate` (`TensorFlowLiteSwift/CoreML`, `.neuralEngine`) | `Interpreter(…, delegates: [CoreMLDelegate()])` + warmup, CPU fallback | **895** | **2640** | **2614** | ~52 |
| ONNX | ANE via Core ML EP (`MLComputeUnits=CPUAndNeuralEngine`, MLProgram, static shapes) | `appendCoreMLExecutionProvider(withOptionsV2:)`, CPU session fallback | **315** | **1342** | **1316** | ~26 |
| Core ML | ANE via `MLModelConfiguration.computeUnits = .cpuAndNeuralEngine` | One-line change | **445** | **729** | **701** | ~14 |

> **Placement is requested, not proven.** `computeUnits`/EP options only
> *permit* ANE; Core ML decides per op and silently falls back to CPU for
> anything it can't place. The only ground truth is Xcode's Core ML instrument
> (Product → Profile → Core ML) while ingesting. Compare against the CPU
> baselines above: embeds are essentially unchanged (LiteRT 2614 vs 2633,
> ONNX 1316 vs 1377, Core ML 701 vs 710), which suggests ANE is **not**
> meaningfully engaged — likely partial delegation with CPU fallback. LiteRT's
> load jump (39 → 895 ms) is the CoreML delegate setup + warmup, independent
> of whether inference landed on the ANE.

How to see which path the code took: each factory logs at info level
(`LiteRT`: "Core ML ANE delegate active…" / "falling back to CPU"; `ONNX`:
"Core ML EP appended…"), visible in a live syslog stream (`idevicesyslog`) or
the Xcode console. `Logger.debug` lines are memory-only and won't persist —
the info lines will. Note the earlier LiteRT device log showed only **66/276
nodes delegated** for this MiniLM `.tflite` (the 3D `Mean` op is unsupported),
with the delegated graph failing at invoke — so the warmup probe most likely
forces a **CPU fallback** on that leaf.

## Why one is faster than another

- **Load time is init work, not inference.** LiteRT mmaps a file (fast:
  39 ms iOS CPU / TBD Android). ONNX Runtime parses the graph and builds a
  session with optimizations (209 ms iOS CPU / ~430 ms Android). Core ML
  additionally compiles/optimizes an execution program for the target compute
  units at `MLModel(contentsOf:)` time (452 ms iOS). With ANE requested the
  order flips: LiteRT's Core ML delegate setup + warmup makes it the slowest
  to load (895 ms), ahead of Core ML (445) and ONNX (315) — see the accelerated
  section above.
- **But load rank ≠ embed rank.** On the iPhone SE per-chunk embed runs
  Core ML (~14 ms) < ONNX (~28 ms) < LiteRT (~53 ms): once compiled, Core ML's
  optimized execution beats both pods' per-invoke overhead. Judge a runtime by
  embed ms for your workload, not by load ms.
- **Embed time is per-token compute.** In the CPU tables above no
  delegates/EPs are wired — all CPU. Drivers, in order: sequence length after tokenizing
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

## LLM leafs (`*/llm/*`)

### Model parity

Same area = same checkpoint within a platform, different runtime + precision — so
outputs are comparable in intent but **not byte-identical**. All LLM leaves run
**Phi-4-mini-instruct** except `ios/llm/coreml` (and the trio's Core ML runtime),
which stay on MiniCPM5-1B + Apple FM — no Phi-4-mini Core ML export fits that
leaf's hand-wired engine (see its README):

| Leaf | Model + runtime | Precision / size |
|---|---|---|
| `android/llm/litert-lm` | Phi-4-mini-instruct via LiteRT-LM `.litertlm` (`litert-community/Phi-4-mini-instruct`) | q8 weights, `ekv4096`, ~3.9 GB |
| `android/llm/onnx-genai` | Phi-4-mini-instruct int4 via ONNX Runtime GenAI (`microsoft/Phi-4-mini-instruct-onnx`) | int4 rtn block-32 acc-level-4, ~4.9 GB |
| `android/llm/dual` | Both of the above behind a runtime picker | ~3.9 GB + ~4.9 GB on disk; one engine resident at a time |
| `ios/llm/litert-lm` | Phi-4-mini-instruct via LiteRT-LM `.litertlm` (`litert-community/Phi-4-mini-instruct`) | q8 weights, `ekv4096`, ~3.9 GB |
| `ios/llm/onnx-genai` | Phi-4-mini-instruct int4 via ONNX Runtime GenAI (`microsoft/Phi-4-mini-instruct-onnx`) | int4 rtn block-32 acc-level-4, ~4.9 GB |
| `ios/llm/trio` | Phi-4-mini-instruct on LiteRT-LM + ONNX GenAI; MiniCPM5-1B on Core ML | ~3.9 GB + ~4.9 GB + ~619 MB |
| `ios/llm/coreml` | **default**: MiniCPM5-1B 4-bit via Core ML (`Reza2kn/MiniCPM5-1B-CoreML-4bit`); option: Apple Foundation Model (system) | Core ML int4 per-block 32, ~619 MB, **128-token context**; Apple FM = different OS model, no download |

> Android LLM started on MiniCPM5-1B, but ORT GenAI rejects its regex pre-tokenizer
> (`Invalid regex: \p{N}+`), and SmolLM2-135M has no ORT-GenAI-compatible ONNX export,
> so every `litert-lm` / `onnx-genai` leaf (both platforms, plus the chooser apps) now
> uses Phi-4-mini-instruct. Only the Core ML path keeps MiniCPM5-1B.

### Measured — Android (`SM_S948B`)

Method: fresh install → launch → Download model → send a one-line prompt → read the
banner (load ms) and the assistant message metrics. `TBD` = not measured yet — fill it
in from your own run rather than guessing.

| Leaf | Model + runtime | Load ms | TTFT ms | tok/s | Notes |
|---|---|---|---|---|---|
| `android/llm/onnx-genai` | Phi-4-mini int4 / ONNX GenAI (CPU) | **3910** | **722** | **48.0** | `cpu-int4-rtn-block-32-acc-level-4`, ~4.9 GB on disk |
| `android/llm/litert-lm` | Phi-4-mini q8 / LiteRT-LM (CPU) | **7418** | **899** warm (5318 cold) | **10.3** | ~3.9 GB `.litertlm` (3.64 GiB); first run compiles the XNNPACK/MLDrift cache, later runs are faster |
| `android/llm/dual` | Phi-4-mini / either runtime | per runtime | per runtime | per runtime | Same engines as the single-runtime leaves; picker selects one, not separately measured |

Reading it: LiteRT-LM's cold first generation (TTFT 5318 ms) includes compiling the
delegate cache (`*_xnnpack_cache`); steady state is ~10 tok/s. On this device ONNX
GenAI is ~4.7× faster per token than LiteRT-LM (48.0 vs 10.3 tok/s) and loads the model
in about half the time, at the cost of the larger int4 download (4.9 vs 3.9 GB).

LiteRT-LM and ONNX GenAI share the Phi-4-mini checkpoint but use different precisions
(q8 vs int4) and quantizers, so throughput is comparable in intent, not exact.

