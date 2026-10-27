# Benchmarks — Vector Search + LLM Runtimes

Interactive version of these tables (platform/metric pickers, head-to-head):
[`docs/benchmarks.html`](benchmarks.html) · how to embed it in Confluence:
[`docs/benchmarks-dashboard.md`](benchmarks-dashboard.md).

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
| `dual` | Both AARs above, ~63 MB download | MiniLM both formats, **86 + 86 MB**, 384-d shared index | **131** (LiteRT) / **489** (ONNX) | **~8–13** (LiteRT) / **~10–16** (ONNX) | Same weights in both files — identical rankings, see the measured section below |

### Measured — `dual`, both runtimes on the same weights (`SM_S948B`, 2026-10-20)

Same app, same corpus, same query; only the runtime changes. Both files are
`all-MiniLM-L6-v2` (fp32, 384-d, `maxSeqLen` 128, mean-pool + L2 in app code, CPU, no
delegates/EPs). The `.tflite` was converted from the official `tf_model.h5`; the
`.onnx` is the official export. Before the device run, both were fed identical token
ids on the host: **cosine 1.000000, max |diff| 1.9e-7** per pooled vector.

Corpus — 5 free-text chunks (blank line = chunk), one deliberately unrelated:
fridge/cold, compressor/refrigerant, thermostat, freezer/−18 °C, quarterly revenue.
Query, typed exactly as below (typo kept so both runs match): `how dose a fridge stay cold`.

| Step | LiteRT (TFLite) | ONNX Runtime |
|---|---|---|
| Model load | **131 ms** | **489 ms** |
| Ingest 5 chunks | **70 ms** (embed 65 / store 5) | **76 ms** (embed 72 / store 4) |
| Embed per chunk, cold → warm | 27 / 13 / 8 / 9 / 8 ms | 24 / 16 / 11 / 10 / 11 ms |
| Query embed | **18 ms** | **18 ms** |
| Rank 5 hits (HNSW) | **1 ms** | **0 ms** |
| Ranked order + scores | fridge **73%**, freezer **58%**, thermostat **42%**, compressor **40%**, revenue **0%** | identical: **73 / 58 / 42 / 40 / 0 %** |

Reading it:

- **Identical results, as expected from identical weights.** Same order, same rounded
  scores, same 0% outlier — the shared 384-d index is interchangeable when both sides
  are fed the same checkpoint.
- **LiteRT loads ~3.7× faster** (131 vs 489 ms): `Interpreter` mmaps the asset, while
  the ONNX factory `readBytes()`s 86 MB into memory and then builds the session.
- **Embedding speed is a wash** (65 vs 72 ms for 5 chunks; 8–11 ms per chunk warm), and
  the per-query embed is 18 ms in both. Ranking is sub-millisecond — at this corpus size
  the index is not the bottleneck.
- **Switching runtimes clears the index**, as designed: the switch logged
  `switched to LiteRT, index cleared` and the next search returned 0 hits. Both models
  stay resident, so switching back is instant.
- Known UI wart: the ingest screen's chunk count and the search results keep their old
  values after a switch (the database is empty; the screen state is stale until the next
  ingest/search).

### Accelerated — ONNX WebGPU (Android, `SM-S938B`, 2026-10-21)

> Device note: the tables above were measured on `SM_S948B`; this run is a different unit
> (`SM-S938B`, 12 GB). Compare the two columns *within this section*, not across sections.

The ONNX lane now takes an execution provider at launch: `--es onnx_ep webgpu` (default
`cpu`). WebGPU is attempted and then validated with a warmup inference — a session that
builds but cannot run (Dawn without a usable Vulkan device, unsupported ops) logs the
reason, rebuilds on CPU, and the stats sheet shows which EP actually ran
(`embedding.onnx · webgpu`). The embedding values are unchanged: same 6-chunk corpus and
query rank identically on both EPs (`74 / 58 / 52 / 42 / 42 %`), and the in-app timings
move the same direction as the loop below (ingest embed 122 → 99 ms, search 28 → 17 ms;
load 504 → 785 ms, because Dawn init + the warmup are paid up front).

Sustained throughput — 120 s of the real embedder in a loop, no DB writes:

| EP | ms/chunk | chunks/s |
|---|---|---|
| CPU (XNNPACK) | 19.2 / 25.2 | 45.4 / 39.8 |
| **WebGPU** | **13.4 / 13.4** | **74.9 / 73.3** |

Energy — unplugged, wireless adb, screen on, `batterystats` reset per run, 120 s each:

| Config | charge counter / 120 s | per 1000 chunks | ≈ energy |
|---|---|---|---|
| idle (app foreground) | 4.9 mAh | — | ~0.6 W |
| CPU | 64.0 mAh (both runs) | ~11–12 mAh | ~160–180 mJ/chunk |
| **WebGPU** | **54.2 / 29.6 mAh** | **~3–6 mAh** | **~40–80 mJ/chunk** |

Reading it:

- **WebGPU is ~1.7× faster and does the same work for roughly 2–3× less energy per chunk.**
  This is the opposite of the LLM WebGPU result, where per-token work was slower and memory
  4× higher — there the model was a 1B decoder on a CPU-optimised graph; here the graph is
  a small encoder and the GPU path is the intended one.
- **The per-UID `batterystats` estimate is unusable for GPU work.** It models CPU time, so
  it reported 4.6 mAh for a run whose pack-level draw was 30–54 mAh. The charge-counter and
  %-level deltas are the honest whole-device numbers; only those are used above.
- **Resolution caveat:** the charge counter updates in ~4.9 mAh steps and `current_now` is
  permission-denied on this Samsung without root, so treat each cell as ±1 step. The
  CPU-vs-GPU gap is well outside that, but the two WebGPU runs (54 vs 30 mAh) are not
  meaningfully different from each other.
- **Thermals favour WebGPU under sustained load:** the second CPU run throttled to
  25.2 ms/chunk (from 19.2), while WebGPU held 13.4 in both runs.

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

### Backend / accelerator (Android)

The Android LLM leaves pick their compute backend at model-load time; there is no UI toggle.

- `android/llm/litert-lm` and the LiteRT-LM half of `android/llm/dual`: **GPU first, CPU fallback** — `Backend.GPU()` is attempted, and a device without an OpenCL driver falls back to `Backend.CPU()` (the info sheet shows which one loaded). **GPU is skipped up front** on a device already known to be unable to hold it: total RAM below `2 × model + 1.5 GiB`, or a GPU load that previously died mid-load. NPU (`Backend.NPU`) is not wired: it needs bundled Qualcomm QNN libraries plus an SoC-specific `.litertlm`.
- `android/llm/onnx-genai` and the ONNX half of `android/llm/dual`: **CPU only**. Not because the API is
  missing — GenAI exposes `Config.appendProvider` and would accept `qnn`/`webgpu` — but because there is
  no accelerated Phi-4-mini artifact in GenAI format (QNN) or a validated one (WebGPU), and QNN context
  binaries are SoC-arch-locked. Evidence: `android/llm/onnx-genai/README.md` → *Acceleration on Android*.

Why the skip matters — the same `.litertlm` needs a very different amount *and kind* of memory per backend:

| Backend | Resident weights | File-backed? | Reclaimable under pressure? |
|---|---|---|---|
| CPU (XNNPACK) | `mmap`ed model, ~2.9 GB RSS | yes | yes — kernel evicts, re-faults from disk |
| GPU (ML Drift / OpenCL) | model **+** ~6.1 GB `GL mtrack` weight copy | **no** | **no** — pinned graphics memory |

So the CPU path fits a 7.6 GB phone that the GPU path OOM-kills. The decision lives in
`GpuPolicy` + `GpuPreflight` — see `android/llm/litert-lm/README.md` → *GPU feasibility gate*.

### Measured — GPU vs CPU, same device (Android, `SM-F966B`)

Galaxy Z Fold7, Snapdragon 8 Elite, 12 GB. One variable: the backend. Same model (Phi-4-mini q8,
~3.9 GB `.litertlm`), same prompt (*"Explain in detail how a refrigerator works"*), fresh launch
per run, 3 runs per backend. `tok/s` is the decode window only (`generatedTokens / (totalMs − ttftMs)`),
so it compares backends; load and TTFT are single-run values in run order.

| Leaf | Backend | Load ms | TTFT ms | tok/s | Generated tokens |
|---|---|---|---|---|---|
| `android/llm/dual` | **GPU** (ML Drift / OpenCL) | 11926 / 12187 / 13031 | 1181 / 1161 / 1248 | **10.7 / 10.6 / 10.2** | 477 |
| `android/llm/dual` | **CPU** (XNNPACK) | 936 / 716 / 713 | 1972 / 780 / 778 | **5.8 / 5.8 / 5.7** | 464 |

Reading it:

- **GPU decodes ~1.8× faster** (10.5 vs 5.8 tok/s). This is the only axis where GPU wins.
- **CPU loads ~17× faster** (0.7–0.9 s vs 12–13 s): it `mmap`s the model, GPU builds a copy.
- **CPU reaches the first token sooner** in steady state (0.78 s vs 1.2 s). CPU's *first* run after
  load pays a one-off delegate compile (TTFT 1972 ms, then 780/778 ms).
- GPU's cost is the **~6.1 GB pinned `GL mtrack` copy** (`MemAvailable` bottomed at 0.66 GB, no
  fallback possible) — which is why it is gated on RAM rather than switched on unconditionally.

The gate, on the two devices that motivated it:

| Device | Total RAM | GPU attempt | After the gate |
|---|---|---|---|
| TCL T807D | 7.6 GB | OOM-killed on every load (`reason=LOW_MEMORY`, `subreason=OOM KILL`) | skips GPU → CPU |
| Galaxy Z Fold7 (`SM-F966B`) | 12 GB | loads in ~12 s, 10.5 tok/s | passes → GPU |

> The CPU baseline on `SM_S948B` (10.3 tok/s, above) is a *different* device — do not compare it with
> these numbers. Only same-device, same-prompt runs are comparable.

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

## LLM re-benchmark — MiniCPM5-1B on Android (`SM_S948B`, 2026-10-20)

> Appended, not replacing anything above: the Phi-4-mini numbers stay as the baseline
> for the leaves still on Phi-4 (`android/llm/dual`, `ios/llm/onnx-genai`). This section
> measures the two Android leaves that now ship **MiniCPM5-1B**.

Protocol — same device, same prompt, same params, fresh launch per run:

- Device: Galaxy S25 Ultra (`SM_S948B`), 11,350,972 kB RAM, Android 17 (SDK 37),
  4 KB pages, on charger, no throttling (`Thermal Status: 0`; skin 37.9 °C, battery
  26.5 → 36 °C over the whole session).
- Prompt: *"Explain how a refrigerator works in detail."* — one turn, no system prompt.
- Params: both leaves' defaults — temperature 0.8, top-k 40, top-p 0.95, max 512 tokens.
- Runs: `am force-stop` + relaunch before each generation (so `load ms` is a cold
  process load with a warm page cache), 3 runs per cell, values read from the banner
  (`loaded in X ms`) and the metrics line (`TTFT X ms · Y tok/s · N tokens`).
- `tok/s` = decode window only (`generatedTokens / (totalMs − ttftMs)`), as above.

### Model parity — MiniCPM5-1B (current)

| Leaf | Model + runtime | Precision / size | Context window |
|---|---|---|---|
| `android/llm/litert-lm` | MiniCPM5-1B via LiteRT-LM `.litertlm` (`minicpm_wi4b32_wi8_afp32_gpu_opt`) | W4 block-32 / W8, fp32 activations, **793 MB** | **1024 tokens** — declared by the bundle (`ModelInfo.maxContextTokens()`, info sheet) |
| `android/llm/onnx-genai` | MiniCPM5-1B int4 via ONNX Runtime GenAI (`anydaytv/minicpm5-1b-onnx-int4`) | int4 rtn block-32 acc-level-4, **1.43 GB** | **131,072 tokens** — checkpoint maximum in `genai_config.json`; ~4k practical (memory-bound) |
| `android/llm/dual` | Still Phi-4-mini behind the runtime picker | not measured here | not measured here |

Context, in one paragraph: MiniCPM5-1B supports **131,072 tokens**, but the two Android packs
declare very different budgets. The LiteRT-LM `_gpu_opt` conversion declares **1024 tokens** — that
is the number the info sheet shows, and prompt + history + output are expected to fit inside it
(the 512-token default output leaves ~480 for the prompt, so multi-turn chats are short). The ONNX
int4 config keeps the full 131,072, but the cache is fp32 at **48 KB/token**
(`24 layers × 2 (K,V) × 2 kv-heads × 128 head-dim × 4 B`), so 128k would cost ~6.3 GB on its own —
~4k tokens (~200 MB) is the realistic ceiling next to 1.43 GB of weights, and the leaf only allocates
`prompt + max-tokens` per run (834 MB PSS measured at 544 tokens). All numbers below were taken with
short prompts, well inside either window.

### Speed

Bold = median of the three runs.

| Leaf | Backend | Load ms | TTFT ms | tok/s | Generated tokens |
|---|---|---|---|---|---|
| `android/llm/litert-lm` | **GPU** (ML Drift / OpenCL) | 3616 / 2828 / 2902 → **2902** | 421 / 436 / 538 → **436** | 67.2 / 71.0 / 71.2 → **71.0** | 544 / 544 / 544 |
| `android/llm/litert-lm` | **CPU** (XNNPACK) | 782 / 296 / 317 → **317** | 565 / 597 / 591 → **591** | 73.7 / 72.3 / 72.6 → **72.6** | 544 / 544 / 544 |
| `android/llm/onnx-genai` | **CPU** | 1079 / 887 / 890 → **890** | 110 / 89 / 90 → **90** | 97.9 / 91.3 / 89.6 → **91.3** | 361 / 544 / 544 |

Reading it:

- **GPU and CPU decode the same at 1B.** 71.0 vs 72.6 tok/s is inside run-to-run noise.
  GPU only buys a ~150 ms earlier first token (436 vs 591 ms) — and pays ~2.6 s more
  load. On Phi-4-mini q8 (above) the same GPU path was **1.8× faster** per token; at
  793 MB the model is small enough that neither backend is dominated by weight traffic.
- **ONNX GenAI is the fastest at steady state** (91.3 vs 72.6 tok/s = 1.26×) and has the
  lowest TTFT (90 ms) — but the slowest load (890 ms: ORT parses/optimizes the graph and
  copies 1.43 GB into native heap) and the largest download.
- The ONNX run that stopped early (361 tokens, EOS) shows the highest tok/s (97.9):
  a shorter decode window means a smaller KV cache. The median is the honest number.

### Memory during decode (`dumpsys meminfo`, one generation per row)

| Leaf | Backend | Total PSS | Total RSS | `GL mtrack` | Notes |
|---|---|---|---|---|---|
| `android/llm/litert-lm` | GPU | **1.80 GB** | 1.93 GB | **1.37 GB** | weights uploaded to a pinned graphics buffer — not reclaimable under pressure |
| `android/llm/litert-lm` | CPU | **918 MB** | 1.04 GB | 5.5 MB | `mmap`ed weights; clean pages are evictable |
| `android/llm/onnx-genai` | CPU | **834 MB** | 958 MB | 5.8 MB | 577 MB native heap — ORT copies the model into anonymous memory |

So the GPU attempt costs **~880 MB more PSS and ~1.37 GB of pinned graphics memory**
for zero decode gain on this bundle. The RAM gate still does its job (it answers "will
it fit", not "is it faster") — see the open question at the end.

### Versus the Phi-4-mini numbers above (same device, same protocol shape)

| Leaf | Metric | Phi-4-mini | MiniCPM5-1B | Delta |
|---|---|---|---|---|
| `android/llm/litert-lm` | tok/s (CPU) | 10.3 | **72.6** | 7.0× |
| `android/llm/litert-lm` | load ms (CPU) | 7418 | **317** | 23× faster |
| `android/llm/onnx-genai` | tok/s (CPU) | 48.0 | **91.3** | 1.9× |
| `android/llm/onnx-genai` | load ms | 3910 | **890** | 4.4× faster |

Different checkpoint, precision and tokenizer: read this as "a 1B model is far cheaper
to run than a 3B one", not as a runtime ranking.

### Caveats

- `generatedTokens` includes the **prompt** in both leaves (544 = 32 prompt + 512
  output), so `tok/s` is ~6% optimistic. The bias is identical in both, so cross-leaf
  comparison holds.
- The ONNX leaf used to ignore the max-tokens slider: GenAI's `max_length` is a
  *whole-sequence* cap, and the old `maxTokens + 1024` let a 512-token request run for
  700–1000 tokens. Measured before the fix: **810 / 699 / 1004 tokens at 88.2 / 90.3 /
  79.4 tok/s**. Fixed on 2026-10-20 (`maxSequenceLength(promptTokens, maxTokens)`),
  which is why the table above is capped at 544. Older ONNX tok/s figures taken from a
  long answer are not comparable to these.
- `android/llm/dual` still runs Phi-4-mini, and `ios/llm/onnx-genai` / the ONNX leg of
  `ios/llm/trio` still point at the Phi-4-mini package, so neither is measured here. The
  iOS MiniCPM5 numbers (LiteRT-LM GPU/CPU + Core ML) are in the iOS section further down.
- **WebGPU on this leaf is a negative result, not a shipped path.** `appendProvider("webgpu")`
  on the same int4 package does load and decode (Dawn's Vulkan backend is in the AAR), but
  measures **71.5 tok/s, TTFT 225–289 ms, load 963–1123 ms, 3.14 GB PSS / 1.50 GB `GL mtrack`**
  against CPU's **91.3 tok/s / 90 ms / 890 ms / 834 MB**. The weights do reach GPU memory, but
  the `-e cpu` export packs `MatMulNBits` for CPU, so per-token work is slower and memory is 4×.
  A fair test needs an `-e webgpu` model build; the provider stays unwired.

### Open question — should a 1B bundle stay GPU-first?

`GpuPreflight` answers "can this device hold the GPU copy", not "is GPU worth it". On
Phi-4-mini q8 the answer was yes (1.8× decode); on MiniCPM5-1B W4 it is neutral-to-worse
(parity, +2.6 s load, +880 MB PSS). Options, not decided yet:

1. Leave as is — room to grow if a larger MiniCPM bundle lands later.
2. Skip GPU below a weight-size threshold (e.g. GPU only when the bundle is > 2 GB).
3. CPU-first for `_gpu_opt` bundles, keeping the GPU attempt behind a manual switch.


## iOS LLM — MiniCPM5-1B on Trio (iPhone SE 3rd gen, 2026-10-21)

> Appended, not replacing anything above. This is the iOS counterpart to the Android
> MiniCPM5 section: same checkpoint, same prompt, same parameters, measured on a
> physical device through the Trio app's real engines. Toolchain and packaging hurdles
> behind these numbers, plus the per-runtime accelerator caveats, are collected in
> [`docs/ios-llm-hurdles.md`](ios-llm-hurdles.md).

Protocol:

- Device: iPhone SE 3rd gen (`iPhone14,6`, A15), **iOS 27.0.1 (24A446)**, 128 GB storage.
- Prompt: *"Explain how a refrigerator works in detail."* — one turn, no system prompt.
- Params: `GenerationParams.default` — temperature 0.8, top-k 40, top-p 0.95, max 512.
- Runs: 3 per cell, each a **cold process**. The app launches with
  `--benchmark litert-gpu|litert-cpu|onnx|coreml` (`Sources/Benchmark/BenchmarkMode.swift`),
  loads the model, runs one generation, writes a JSON report to `Documents/` and exits.
  Normal launches are unaffected; the engines, params and metrics code are the same ones
  the chat UI uses.
- `tok/s` = decode window only (`generatedTokens / (totalMs − ttftMs)`), same formula as Android.
- Thermal matters on this phone: a CPU batch taken straight after ~11 minutes of Core ML
  at full tilt read **25.6–26.4 tok/s**; after a 10-minute cooldown the same binary read
  **35.5–35.7**. The table is the cooled batch. The SE has no vapour chamber, so sustained
  runs (or hot-room runs) will land nearer the lower number.

### Model parity

| Runtime | Model + format | Precision / size | Context window |
|---|---|---|---|
| `litert-lm` | MiniCPM5-1B `.litertlm` (`minicpm_wi4b32_wi8_afp32_gpu_opt`) | W4 block-32 / W8, fp32 activations, **793 MB** | **1024** — declared by the bundle (`ModelInfo.maxContextTokens()`) |
| `onnx-genai` | MiniCPM5-1B int4 (`anydaytv/minicpm5-1b-onnx-int4`, same package as Android) | int4 rtn block-32 acc-level-4, **1.43 GB** | **131,072** — checkpoint max; ~4k practical (fp32 KV, 48 KB/token) |
| `coreml` | MiniCPM5-1B 4-bit `.mlpackage` (`Reza2kn/MiniCPM5-1B-CoreML-4bit`) | 4-bit, **602 MB** on disk | **128** — fixed stateless window, no attention mask |

### Speed

Bold = median of the three runs.

| Runtime | Backend | Load ms | TTFT ms | tok/s | Tokens | Total ms |
|---|---|---|---|---|---|---|
| `litert-lm` | **GPU** (Metal) | 1090 / 1158 / 1301 → **1158** | 978 / 338 / 360 → **360** | 43.7 / 43.8 / 43.8 → **43.8** | 533 | 13186 / 12496 / 12524 |
| `litert-lm` | **CPU** (XNNPACK) | 456 / 433 / 427 → **433** | 2320 / 1252 / 1233 → **1252** | 35.5 / 35.7 / 35.7 → **35.7** | 278 | 10146 / 9034 / 9016 |
| `onnx-genai` | **CPU** | 593 / 740 / 635 → **635** | 236 / 202 / 207 → **207** | 46.6 / 46.8 / 43.1 → **46.6** | 392–533 | 11675 / 8569 / 11170 |
| `coreml` | **ANE/GPU/CPU** (`.cpuAndNeuralEngine`) | 30061 (first ever, package compile) then 1473 / 1480 → **1480** | 4019 / 1753 / 3386 → **3386** | 2.1 / 2.0 / 2.0 → **2.0** | 337–512 | 200687 / 264316 / 172963 → **200687** |

Reading it:

- **ONNX GenAI wins this phone outright** — 46.6 tok/s, TTFT 207 ms, load 635 ms, better
  than LiteRT-LM GPU (43.8 / 360 / 1158) on all three axes, at the cost of a bigger download
  (1.43 GB vs 793 MB). It is also the same `anydaytv/minicpm5-1b-onnx-int4` package the
  Android leaf runs, so this is the one iOS/Android cell that compares the identical
  weights file — the closest thing to a pure runtime comparison in this document.
- **GPU beats CPU on both axes here** — 43.8 vs 35.7 tok/s (1.23×) and TTFT 360 vs 1252 ms,
  for +0.7 s of load (1158 vs 433 ms). Unlike the Android S25 Ultra, where the same 1B bundle
  made GPU and CPU a dead heat, the SE's CPU path is the weaker one. The GPU cell is a real
  Metal run, not a requested-but-silently-fallen-back one: the device log shows
  `name=GPU Metal`, a created Metal device and both graphs delegated to the Metal
  `InferenceContext` (only the sampler is CPU-side). Excerpt and caveats:
  [`docs/ios-llm-hurdles.md`](ios-llm-hurdles.md#proof-that-the-litert-lm-gpu-run-is-real).
- **First launch is not steady state.** The very first process to touch the bundle ever paid
  5634 ms load / 959 ms TTFT (Metal pipeline compilation); the first run of each later batch
  still shows the TTFT bump (978 ms) before settling at 338–360 ms. Core ML is the extreme
  case: 30 s on the first launch (`MLModel.compileModel`), 1.5 s once the compiled model is cached.
- **Core ML is 20× slower per token and not comparable.** The engine has no KV cache — it
  re-runs the whole 128-token window and reads a 130,560-wide logits row for every token — so
  2.0 tok/s is the price of the hand-wired loop, not of the ANE. It is included because the
  trio ships it, not as a runtime race.
- **Token counts differ because sampling differs, not counting.** LiteRT GPU always reaches
  the 512 cap (533 = 512 output + ~21 chat-template/prompt tokens, the same accounting as the
  Android 544 = 512 + 32); CPU stops at EOS after 278; ONNX lands between 392 and 533 as EOS
  fires; Core ML slides its window and lands between 337 and 512.
- **Cross-platform, same checkpoint:** S25 Ultra `litert-lm` GPU 71.0 tok/s vs iPhone SE 43.8;
  TTFT 436 vs 360 ms (warm GPU). Different SoC classes — this compares a current flagship
  against a 2022 budget phone, so it is not a runtime verdict.
- **No memory column.** iOS has no `dumpsys meminfo` equivalent from the CLI; a footprint
  number needs Instruments' Activity Monitor during a run, and would still not be
  comparable to Android's PSS.

### Battery and energy (Power Profiler, 5 generations per cell)

Cross-platform battery/energy page (Android + iOS, methods and limits):
[`docs/benchmarks_batter.md`](benchmarks_batter.md).

The app can read `UIDevice.batteryLevel`, but iOS quantises it (roughly 5%) and updates it
late: a 77 s run once read **85% → 80%** while the power model accounted for only ~1.2% of
the pack. Battery percentage alone therefore cannot resolve a single generation. Instead,
`xctrace` **Power Profiler** was attached to the running process
(`--attach MyLeoLLMTrio`) and its `SystemPowerLevel` table exported — one `power-usage`
sample per second in **%/hour**, plus display brightness.

Protocol: same prompt and params, **5 generations per runtime in one process**, screen held
on (`isIdleTimerDisabled`), brightness ~20%, device unplugged, thermal state `fair` after the
preceding runs — which is why `tok/s` here is lower than the cooled table above. Energy is
converted with the SE 3 pack: **2018 mAh, 3.86 V nominal**.

| Runtime | tok/s (this run) | mean %/hr | peak %/hr | ≈ power | 5-run energy | ≈ mJ/token |
|---|---|---|---|---|---|---|
| `onnx-genai` CPU | 43.5 | **78.0** | 100.0 | **6.1 W** | 331 J | 130 |
| `litert-lm` GPU | 35.9 | **59.9** | 104.1 | **4.7 W** | 325 J | 122 |
| `litert-lm` CPU | 29.2 | **48.0** | 82.9 | **3.7 W** | 185 J | 133 |

Reading it:

- **Energy per token is essentially flat (122–133 mJ) across all three.** ONNX is the
  fastest and draws the most power; LiteRT CPU is the slowest and draws the least; per token
  they land in the same place. On this phone, the runtime choice changes wall time far more
  than energy per token.
- **LiteRT GPU is nominally the most efficient per token** (~122 mJ), but its margin over
  ONNX (130) is inside this measurement's accuracy — not a real win.
- **Thermals move throughput more than power does.** These runs are `fair` (throttled):
  LiteRT GPU reads 35.9 tok/s here versus 43.8 in the cooled table, while drawing ~4.7 W.
- **These are power-model estimates, not a meter.** iOS gives apps no current/wattage API;
  the samples include the display, and %/hr → watts assumes 2018 mAh at 3.86 V. Compare
  runtimes on this phone, not across devices.
- Attaching Power Profiler adds a little sampling overhead, applied equally to all rows.

How to reproduce: `xctrace record --template 'Power Profiler' --device <udid> --attach
MyLeoLLMTrio --time-limit 120s --output run.trace`, then
`xctrace export --input run.trace --xpath '/trace-toc/run[@number="1"]/data/table[@schema="SystemPowerLevel"]'`.
The app side is `--benchmark <id> --bench-repeats 5` (plus `--bench-wait-unplugged` when the
cable cannot be removed during setup).

## Decision leafs (`*/decision/*`)

The structured-decision area (Julia-1: state + typed question → one probability per
option) has its own benchmark format — the built-in 18-prompt suite (8 multi-answer,
5 score, 5 yes/no; one forward pass per prompt) with per-prompt and per-type timings,
model-load timings, download timings and battery sampling, plus a cross-runtime
comparison card in the `dual` leaf.

Measured run — `android/decision/dual` on `SM-S938B` (2026-10-25): LiteRT GPU FP32
(window 512) vs ONNX Runtime CPU (window 1024), identical answers on all 18 prompts,
ONNX ~1.8× faster per prompt (42 vs 76 ms average). Full tables, model download URLs
and the procedure:
[`docs/benchmarks-decision.md`](benchmarks-decision.md).

