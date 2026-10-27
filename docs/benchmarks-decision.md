# Decision Dual — Benchmark Run (SM-S938B)

The F-DEC-6/F-DEC-7 18-prompt suite run on a Samsung Galaxy SM-S938B, both runtime lanes, with
the measured download, load, per-prompt and battery numbers from that session. Export sources:
`Downloads/julia-1-suite-litert-20261025-155150.{md,json}` and
`Downloads/julia-1-suite-onnx-20261025-155214.{md,json}` on the device (pull them with the
procedure below and regenerate this document from your own run).

## Device

- Model: `SM-S938B` (Samsung, `pa3qxeea`), Android 17 (SDK 37)
- Connection: adb over Wi-Fi debug (mDNS TLS), serial `adb-R5CY21EG0JB-wVjMox._adb-tls-connect._tcp`
  - A second device (`SM_F966B`, USB) was attached at the same time, so every adb/Gradle
    command pinned the target: `ANDROID_SERIAL=<serial>`.

## Model packages and download URLs

The app fetches from `https://huggingface.co/<repo>/resolve/main/<file>` (`JuliaFileDownloader`);
the leaf pins the revisions below, so the pinned URLs are the reproducible ones.

LiteRT — `litert-community/Julia-1-LiteRT` @ `8b3733e196c201ee32e8f5063211588c7f87dd7e`
(Apache-2.0), into `files/models/julia-1-litert/`:

| File | Bytes | URL (append to `https://huggingface.co/litert-community/Julia-1-LiteRT/resolve/8b3733e196c201ee32e8f5063211588c7f87dd7e/`) |
|---|---:|---|
| `tokenizer.json` | 34,363,188 | `tokenizer.json` |
| `julia1_token_table_fp16.bin` | 196,608,000 | `julia1_token_table_fp16.bin` |
| `julia1_s512_fp32.tflite` | 185,095,348 | `julia1_s512_fp32.tflite` |
| `julia1_s1024_fp32.tflite` | 188,503,220 | `julia1_s1024_fp32.tflite` |
| **Total** | **604,569,756** | |

ONNX — `SupersonicLabs/Julia-1-ONNX` @ `82a2fadf8fccfccdc5fd4e1009ba8f1a265eb7a8`
(Apache-2.0), into `files/models/julia-1-onnx/`:

| File | Bytes | URL (append to `https://huggingface.co/SupersonicLabs/Julia-1-ONNX/resolve/82a2fadf8fccfccdc5fd4e1009ba8f1a265eb7a8/`) |
|---|---:|---|
| `tokenizer.json` | 34,363,188 | `tokenizer.json` |
| `model.onnx` | 2,988,923 | `model.onnx` |
| `model.onnx.data` | 576,782,336 | `model.onnx.data` |
| **Total** | **614,134,447** | |

Both `tokenizer.json` files are byte-identical (sha256 `609d8f4c…`), so the same request encodes
to the same token ids on either runtime.

Observed download timing on the device (in-app fetch, file-mtime observation, ±few seconds):

| Package | Bytes | Wall time | Throughput |
|---|---:|---:|---:|
| LiteRT (4 files) | 604,569,756 | ~55–60 s | ~10 MB/s avg, ~29 MB/s peak |
| ONNX (3 files) | 614,134,447 | ~30–35 s | ~18–20 MB/s avg, ~27 MB/s peak |

## Test data

- The 18 prompts live in
  `android/decision/dual/app/src/main/java/org/ggomes/myleo/decision/dual/domain/DecisionSuite.kt`:
  8 multi-answer (`route-billing` … `area-battery`), 5 score (`score-urgency-outage` …
  `score-confidence-session`), 5 yes/no (`noul-refund` … `noul-account-access`). One forward pass
  per prompt, in that order.
- Upstream sources the prompts and parity data come from:
  - LiteRT repo: [`android/scripts/TEST_DATA.md`](https://huggingface.co/litert-community/Julia-1-LiteRT/blob/main/android/scripts/TEST_DATA.md),
    [`JuliaTestData.kt`](https://huggingface.co/litert-community/Julia-1-LiteRT/blob/main/android/app/src/test/java/com/julia1/JuliaTestData.kt),
    [`presets.json`](https://huggingface.co/litert-community/Julia-1-LiteRT/blob/main/android/app/src/main/assets/presets.json)
  - ONNX repo: [`parity-cases.json`](https://huggingface.co/SupersonicLabs/Julia-1-ONNX/blob/main/parity-cases.json),
    [`parity-cpu.json`](https://huggingface.co/SupersonicLabs/Julia-1-ONNX/blob/main/parity-cpu.json),
    [`metrics/accuracy-20260924.json`](https://huggingface.co/SupersonicLabs/Julia-1-ONNX/blob/main/metrics/accuracy-20260924.json)

## Procedure

1. Install from `android/decision/dual/`:
   `ANDROID_SERIAL=<serial> ./gradlew :app:installDebug`, then launch the app.
2. On the LiteRT chip tap the download action (~604 MB), wait for `ready`; repeat on the ONNX
   chip (~614 MB). A switch never re-downloads.
3. Select the **Suite (18)** mode chip, then **Run 18 prompts**, per lane. Progress and Cancel
   are live; the report card appears on completion.
4. Export **Markdown** and **JSON** from each lane's report card (written to `Downloads/` via
   MediaStore, no permissions), then pull:
   `adb -s <serial> pull /sdcard/Download/julia-1-suite-<runtime>-<stamp>.<md|json>`.
5. Once both lanes have a report in one session, the **Runtime comparison** card appears.

Keep the device unplugged with power-save off; the report samples battery around each run.

## Load timings (model into memory)

From the session's report cards (`SuiteReport.loadMs` = tokenizer + graph):

| Lane | Run | Total | Tokenizer | Graph |
|---|---|---:|---:|---:|
| LiteRT (GPU FP32, window 512) | first load after process start | 3093 ms | 2005 ms | 1088 ms |
| LiteRT | warm reload after lane switch | 2034 ms | 1425 ms | 608 ms |
| ONNX (CPU, window 1024) | warm reload after lane switch | 1973 ms | 1420 ms | 553 ms |

The tokenizer (34 MB JSON → BPE tables) dominates; the graph row for LiteRT includes the GPU
delegate compilation.

## Results — LiteRT (GPU FP32, window 512)

Load 3093 ms · prompts 18 · prompt time 1375 ms · average 76 ms · 69–108 ms · wall 1393 ms

| Type | Count | Total | Average | Min | Max |
|---|---:|---:|---:|---:|---:|
| multi-answer | 8 | 615 ms | 77 ms | 69 ms | 108 ms |
| score | 5 | 383 ms | 77 ms | 71 ms | 86 ms |
| yes/no | 5 | 378 ms | 76 ms | 72 ms | 81 ms |

| ID | Type | Tokens | Total | Answer |
|---|---|---:|---:|---|
| route-billing | multi-answer | 62 | 108 ms | → Returns and refunds |
| agent-supervisor | multi-answer | 80 | 69 ms | → Stop the run and roll back |
| review-sentiment | multi-answer | 57 | 72 ms | → Some praise and some complaints |
| language-french | multi-answer | 39 | 73 ms | → Spanish |
| queue-escalation | multi-answer | 57 | 70 ms | → Normal support queue |
| resolution-damaged | multi-answer | 53 | 71 ms | → Refund the purchase |
| intent-account | multi-answer | 58 | 74 ms | → A change to their account details |
| area-battery | multi-answer | 57 | 78 ms | → Battery and power |
| score-urgency-outage | score | 59 | 71 ms | 2.98 / 3 |
| score-review-tone | score | 41 | 86 ms | 0.30 / 3 |
| score-complexity-migration | score | 48 | 82 ms | 1.94 / 3 |
| score-risk-drop-table | score | 55 | 72 ms | 0.22 / 3 |
| score-confidence-session | score | 54 | 71 ms | 0.00 / 3 |
| noul-refund | yes/no | 46 | 81 ms | P(true) = 91.9% |
| noul-deadline | yes/no | 40 | 73 ms | P(true) = 65.9% |
| noul-production-change | yes/no | 41 | 72 ms | P(true) = 100.0% |
| noul-seller-answered | yes/no | 41 | 74 ms | P(true) = 12.2% |
| noul-account-access | yes/no | 40 | 78 ms | P(true) = 18.8% |

## Results — ONNX (CPU, window 1024)

Load 1973 ms · prompts 18 · prompt time 760 ms · average 42 ms · 27–71 ms · wall 764 ms

| Type | Count | Total | Average | Min | Max |
|---|---:|---:|---:|---:|---:|
| multi-answer | 8 | 412 ms | 52 ms | 34 ms | 71 ms |
| score | 5 | 190 ms | 38 ms | 32 ms | 47 ms |
| yes/no | 5 | 158 ms | 32 ms | 27 ms | 34 ms |

| ID | Type | Tokens | Total | Answer |
|---|---|---:|---:|---|
| route-billing | multi-answer | 62 | 71 ms | → Returns and refunds |
| agent-supervisor | multi-answer | 80 | 64 ms | → Stop the run and roll back |
| review-sentiment | multi-answer | 57 | 55 ms | → Some praise and some complaints |
| language-french | multi-answer | 39 | 34 ms | → Spanish |
| queue-escalation | multi-answer | 57 | 50 ms | → Normal support queue |
| resolution-damaged | multi-answer | 53 | 43 ms | → Refund the purchase |
| intent-account | multi-answer | 58 | 45 ms | → A change to their account details |
| area-battery | multi-answer | 57 | 51 ms | → Battery and power |
| score-urgency-outage | score | 59 | 47 ms | 2.98 / 3 |
| score-review-tone | score | 41 | 32 ms | 0.30 / 3 |
| score-complexity-migration | score | 48 | 35 ms | 1.94 / 3 |
| score-risk-drop-table | score | 55 | 38 ms | 0.22 / 3 |
| score-confidence-session | score | 54 | 38 ms | 0.00 / 3 |
| noul-refund | yes/no | 46 | 32 ms | P(true) = 91.9% |
| noul-deadline | yes/no | 40 | 27 ms | P(true) = 65.8% |
| noul-production-change | yes/no | 41 | 34 ms | P(true) = 100.0% |
| noul-seller-answered | yes/no | 41 | 34 ms | P(true) = 12.2% |
| noul-account-access | yes/no | 40 | 32 ms | P(true) = 18.9% |

## Runtime comparison

| Metric | LiteRT (GPU FP32) | ONNX (CPU) |
|---|---:|---:|
| Prompt time (18 prompts) | 1375 ms | 760 ms |
| Average per prompt | 76 ms | 42 ms |
| multi-answer avg | 77 ms | 52 ms |
| score avg | 77 ms | 38 ms |
| yes/no avg | 76 ms | 32 ms |
| Wall clock | 1393 ms | 764 ms |
| Model load | 3093 ms | 1973 ms |
| Answers | identical on all 18 prompts (float noise ≤ 0.1 pp, e.g. 65.9% vs 65.8%) | same |

ONNX Runtime CPU (4 intra-op threads) beat LiteRT's GPU FP32 delegate by ~1.8× on this device,
while producing the same decisions — the shared tokenizer and strict encoding keep the lanes
directly comparable.

## Battery and energy

Cross-platform battery/energy page: [`docs/benchmarks_batter.md`](benchmarks_batter.md).

From the reports (in-app `BatteryManager` sampling around each run):

| Lane | Level | Charge Δ | Current at end |
|---|---|---:|---:|
| LiteRT | 77% → 77% | 0.000 mAh | -2755 mA |
| ONNX | 77% → 77% | 0.000 mAh | -4921 mA |

The suite completes in ~1.4 s per lane, so the charge-counter delta stays at 0.000 mAh — one
suite run is below the gauge's resolution. The instantaneous current reading at run end (the
report's `currentNowUa`) shows the draw while the graph was still settling: ~2.8 A on the LiteRT
GPU lane and ~4.9 A on the ONNX CPU lane (negative = discharging). For energy-per-decision
estimates, run the suite repeatedly back-to-back and divide the accumulated charge delta; the
per-prompt energy difference at these speeds is in the microamp-hour range either way.

## Session notes

- The first ONNX suite attempt this session failed on every prompt with
  `marker 10 outside token_logits`: `OnnxDecider` fed the graph's per-option logits through
  `JuliaDecoder.gather`, which indexes by sequence position (the LiteRT convention). The ONNX
  graph gathers internally (`marker_pos`/`marker_mask` in, one logit per option out — the
  author's `parity.py` slices `candidate[i, :size]`), so `OnnxDecider` now decodes the runner
  output directly. Regression tests in
  `android/decision/dual/app/src/test/.../data/OnnxDeciderTest.kt` fail on the
  old code and pass on the fix; the numbers above are from the fixed build.
- The LiteRT lane answered `language-french` with "Spanish" — that is the model's own decision
  on this prompt, reported as-is; both lanes agree.

## iOS — first device run (iPhone 15 Pro, 2026-10-27)

> The 18-prompt suite is not ported to iOS yet; this is the **single-request** benchmark both
> iOS lanes already support, with the identical request used in the Android session below.
> Hurdles behind these numbers (Metal refusal, bool tensor, signature-prefixed names):
> [`docs/ios-decision-hurdles.md`](ios-decision-hurdles.md).

Device: **iPhone 15 Pro (`iPhone16,1`), iOS 27.0.1**. Three cold processes per lane, same prompt:

- state: *"My invoice shows a charge I did not authorise for a subscription I cancelled."*
- question `choice`: **Which department should handle this ticket?**
  - `billing` — Billing and invoice questions
  - `tech` — Technical problem with the product
  - `returns` — Returns and refunds
- strict encoding: 45 tokens; LiteRT window **512**, ONNX window **1024**

### iOS LiteRT runtime versions

Both generations were measured on the same request, same phone; the **2.17 pod remains the
default** because it is faster for this graph, and the 2.3 lane is opt-in (`--litert23`).

| LiteRT runtime | Lane | Graph ms | Total ms | Notes |
|---|---|---|---|---|
| 2.17 (`TensorFlowLiteSwift` pod) | CPU, 4 threads, XNNPACK on | **452** | **473** | default; Metal delegate cannot compile the graph at any precision |
| 2.3 (`CLiteRT` + Metal accelerator) | CPU | 709 | 728 | modern kernels, slower here |
| 2.3, GPU requested | "GPU" → actually CPU | 697 | 717 | accelerator framework embedded but **not registered**; LiteRT logs `GPU accelerator could not be loaded`, so this is not a GPU result |
| 2.17, Core ML delegate requested | "Core ML" → actually CPU | 455 | 477 | `CoreMLDelegate` (devices `.all`) fails `Failed to create the interpreter.` |

The iOS LiteRT bench therefore keeps the 2.17 figures as its LiteRT row; the 2.3 experiment is
recorded so the comparison is not lost.

| Lane | Load ms | Prepare ms | Graph ms | Total ms | Answer |
|---|---|---|---|---|---|
| LiteRT (TFLite 2.17, CPU fallback) | 2631 / 2686 / 2737 → **2686** | 4.4–4.6 | 446 / 452 / 454 → **452** | 466 / 473 / 474 → **473** | `billing` |
| **ONNX (ORT 1.30, C API, CPU)** | 3089 / 2947 / 2916 → **2947** | 4.7 | 34 / 32 / 27 → **32** | 39 / 37 / 32 → **37** | `billing` |

Reading it:

- **ONNX is ~14× faster per decision on iOS** (32 vs 452 ms graph; 37 vs 473 ms total). This is
  the inverse of the iOS LLM result, where ONNX was only ~1.1× ahead — there both runtimes used
  their modern stacks, while here LiteRT runs on the older TFLite pod and ONNX on ORT 1.30.
- **LiteRT's GPU lane does not exist on iOS.** `MetalDelegate` with precision loss disabled
  (FP32, as the contract requires) fails to create an interpreter for this graph, so every
  "GPU" run reports `ran=CPU`. The GPU/CPU comparison is therefore CPU vs CPU, with
  `sameChoice=true` and `maxProbDiff=0.000000`.
- **Load is dominated by the tokenizer**, not the graph: 2.6–3.1 s for parsing the 34 MB
  `tokenizer.json` plus session/table setup. Android reports 3.1 s (LiteRT) and 2.0 s (ONNX) on
  the same Claude-adjacent contract, so this is consistent.
- The **Trio app reproduces these numbers end-to-end** (`--bench-decision`): LiteRT graph
  438–452 ms, ONNX 24.5–24.7 ms, in one process on the same phone.
- Answers and probabilities are identical across lanes and runs:
  `billing`, `0.999999 / 0.000001 / 0.000000`.
- Android's suite remains the richer benchmark (18 prompts, per-type timings). Porting it to
  iOS — plus the battery/thermal sampling used by the Android report — is the next step for
  like-for-like numbers.

## iOS — 18-prompt suite (iPhone 15 Pro, 2026-10-27)

The same 18 built-in prompts as the Android suite (8 multi-answer, 5 ordered score, 5 yes/no),
run in order on both iOS lanes in one process. Raw exports:
[`docs/reports/julia-1-suite-ios-litert.md`](reports/julia-1-suite-ios-litert.md) ·
[`docs/reports/julia-1-suite-ios-onnx.md`](reports/julia-1-suite-ios-onnx.md) (JSON next to each).

- Device: iPhone 15 Pro, iOS 27.0.1. LiteRT window 512, ONNX window 1024. Both CPU.
- Load: LiteRT **2670 ms**, ONNX **3032 ms** (tokenizer-dominated, as on the single request).
- Overall: LiteRT **476.4 ms** prompt average (451.5–488.8), wall **8.58 s**;
  ONNX **36.8 ms** (26.7–53.8), wall **0.66 s**.

| Type | Count | LiteRT avg | LiteRT min–max | ONNX avg | ONNX min–max |
|---|---:|---:|---|---:|---|
| choice | 8 | 467.6 ms | 451.5–480.6 | 41.7 ms | 26.7–53.8 |
| score | 5 | 480.8 ms | 477.1–483.7 | 36.2 ms | 31.2–42.6 |
| noul | 5 | 486.0 ms | 482.6–488.8 | 29.6 ms | 26.8–31.5 |

Reading it:

- **Same decisions, tiny numeric drift.** All 8 choice ids and all 5 yes/no calls match across
  lanes. The only differences are score/probability roundings: `score-review-tone` 0.298 vs
  0.299, `score-complexity-migration` 1.944 vs 1.945, `score-risk-drop-table` 0.221 vs 0.222,
  `noul-deadline` 0.659 vs 0.658, `noul-account-access` 0.188 vs 0.189 — well inside
  quantisation, not a semantic disagreement.
- **ONNX is ~13× faster on the suite**, consistent with the single request (36.8 vs 476.4 ms).
- **Battery is flat (80% → 80%)** because the whole suite is ~0.7–8.6 s; the iOS gauge cannot
  resolve that. Thermal state was `nominal` throughout. For power numbers, the repeat-based
  Power Profiler method in `docs/benchmarks_batter.md` is the right tool.
- The suite is deliberately the same 18 prompts as Android, so the per-prompt answers can be
  diffed directly from the JSON exports.
