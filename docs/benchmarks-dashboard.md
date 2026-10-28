# Benchmarks Dashboard (HTML) — and how to put it in Confluence

An interactive, **self-contained** page: platform picker (Android / iOS), area (LLM / Vector
search / Decision), device filter, metric selector (tok/s, TTFT, load, power, energy per
token/chunk, embed ms, decision ms, PSS), a sorted bar chart, a head-to-head card and the full
table. No network calls, no CDN, no build step.

Each area opens on the metric that answers “which runtime is faster?” — decode tok/s for
LLM, embed ms/chunk for vector search, decision ms/prompt for the Julia-1 suite. It does not
default to *load* (ONNX loads ~0.3 s slower on the decision lane yet is ~13× faster per prompt).

The head-to-head cards are followed by a **side-by-side table** covering every metric both rows
have — decode speed, TTFT, model load, average power, energy per token/chunk/decision and memory.
Power and energy are the battery proxies (iOS has no current API); the better cell is highlighted
and the last column is B relative to A.

Two collapsible panels sit above and below the chart:

- **Methodology — what ran** (open by default): the model, prompt/corpus/suite and parameters for
  the selected area, plus the iOS and Android device notes for the selected platform(s). It
  follows the platform and area selectors.
- **Notes & quirks for this selection** (collapsed): platform/runtime caveats and the per-row
  notes, filtered to the current selection. A runtime quirk only appears when that runtime is in
  the filtered rows, so e.g. the Core ML note is absent unless Core ML is on screen.

Current contents: **19 rows** across Android and iOS, covering LLM, vector search and the
Julia-1 decision suite. The dashboard is the interactive view of the Markdown tables and must
be updated whenever a benchmark is documented.

Files:

| File | Purpose |
|---|---|
| `docs/benchmarks.html` | The dashboard (data embedded as JSON). Open in any browser. |
| `docs/benchmarks-data.csv` | Same rows, flat — for Confluence chart macros / spreadsheets. |
| `docs/benchmarks-dashboard.png` | Static preview (also the fallback image for pages without JS). |

Data sources: [`benchmarks.md`](benchmarks.md), [`benchmarks_batter.md`](benchmarks_batter.md),
[`benchmarks-decision.md`](benchmarks-decision.md). Edit the JSON block in the HTML and the
page updates; the CSV is generated from that block.

## Use it locally

```bash
open docs/benchmarks.html          # macOS
# or: python3 -m http.server -d docs 8000  → http://localhost:8000/benchmarks.html
```

Everything works from `file://` — no server needed.

### Standalone snapshot (one file)

`docs/standalone-benchmarks.html` is the single-file variant: inline CSS, inline data and
inline JS, so it needs no images, no CSV/PNG siblings and no network. It is interactive —
area tabs, platform/device filters, free-text search, chart-metric switch, sortable table
headers, a `<dialog>` with the full row, and a client-side CSV export — and falls back to
static tables under `<noscript>` (or when a host strips scripts). Regenerate it after
editing the dashboard data:

```bash
python3 docs/tools/standalone_benchmarks.py   # reads bench-data, writes the snapshot
```

## Update the numbers

1. Edit the `<script id="bench-data" type="application/json">` block in
   `docs/benchmarks.html` (one object per measured row).
2. Regenerate the CSV (or use the page's **Download CSV** button):

```bash
python3 - <<'PY'
import json, csv, io, re
from pathlib import Path
html = Path("docs/benchmarks.html").read_text()
rows = json.loads(re.search(r'id="bench-data" type="application/json">(.*?)</script>', html, re.S).group(1))
keys = ["platform","area","device","runtime","backend","model","loadMs","ttftMs","tokPerSec",
        "powerW","mjPerToken","msPerChunk","mjPerChunk","msPerPrompt","pssMb","note"]
buf = io.StringIO(); w = csv.DictWriter(buf, fieldnames=keys, extrasaction="ignore"); w.writeheader()
[w.writerow({k: ("" if r.get(k) is None else r.get(k)) for k in keys}) for r in rows]
Path("docs/benchmarks-data.csv").write_text(buf.getvalue())
PY
```

Fields are optional per row: a metric the runtime doesn't have (e.g. `powerW` for Core ML) is
simply omitted, and the chart says how many rows were excluded.

3. Re-render the static preview (also the Confluence fallback):

```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu \
  --hide-scrollbars --window-size=1200,1500 \
  --screenshot="$PWD/docs/benchmarks-dashboard.png" "file://$PWD/docs/benchmarks.html"
```

**Rule:** a benchmark change is not finished until `benchmarks.html`, `benchmarks-data.csv` and
`benchmarks-dashboard.png` reflect it. This is enforced in `AGENTS.md` → *Benchmarks dashboard*.

## Confluence

There is no single portable answer: it depends on Cloud vs Data Center and on which macros are
installed. In order of quality:

### 1. Data Center / Server — built-in **HTML macro**

Paste the entire contents of `docs/benchmarks.html` into the macro (with "HTML" rendering).
The page is self-contained, so it survives Confluence's CSP as long as scripts are allowed;
the chart, filters and head-to-head work. Downloads may be blocked inside the macro's sandbox —
the visualisations are not.

### 2. Cloud or DC — **HTML Include / HTML for Confluence** (Marketplace app)

Host the page somewhere reachable (next section) and point the macro at the URL, or paste the
file inline if the app supports raw HTML. These apps are the common way to run scripted content
on Confluence Cloud, where the built-in HTML macro does not exist.

### 3. Cloud — **Widget Connector** (built-in) with a hosted URL

Works when the provider/URL is accepted by the macro; generic iframes are often rejected. Test
before relying on it. The page sends no `X-Frame-Options`, so a permitted iframe will render.

### 4. No apps at all — **Chart from Table** + CSV / static image

- Attach `docs/benchmarks-data.csv` to the page and use the *Chart from Table* (or *Table
  Filter and Charts*) macro: select `runtime` as label, one metric as value, filter by
  `platform`/`area`. That gives a native bar chart with the page's own table filters — less
  flexible than the HTML page, but zero custom code.
- Or attach `docs/benchmarks-dashboard.png` and link the hosted page for the interactive
  version. Good for exec-facing pages where scripts are unwelcome.

### Hosting for options 2–3 (GitHub Pages)

The repo already keeps the page under `docs/`, which GitHub Pages can serve directly:

1. Repository → Settings → Pages → *Build and deployment*: “Deploy from a branch”, branch
   `main` (or `squashed`), folder **`/docs`**.
2. The URL becomes `https://<owner>.github.io/<repo>/benchmarks.html`.
3. In Confluence, embed that URL with HTML Include (preferred) or Widget Connector.

If Pages is not desirable, any static host works (S3, Netlify, an internal web server); the file
has no dependencies.

### Known limitations inside Confluence

- Sandboxed iframes may block the **Download CSV/SVG** buttons; the charts still render.
- Deep links into a filter state are not stored in the URL yet (planned if it matters).
- The HTML macro is not available on Confluence Cloud without a Marketplace app.
- Confluence's PDF export renders the static PNG, not the interactive page.

## What the page plots

| Metric | Where it comes from | Better |
|---|---|---|
| Decode speed (tok/s) | LLM runs, decode window only | higher |
| Time to first token | LLM runs | lower |
| Model load | LLM / decision / embedding load | lower |
| Average power (W) | iOS Power Profiler, Android charge counter | lower |
| Energy per token / chunk | derived from the above + token/chunk counts | lower |
| Embedding per chunk | vector-search leaves | lower |
| Decision per prompt | Julia-1 suite (18 prompts) | lower |
| Memory (PSS) | Android `dumpsys meminfo` only (no iOS equivalent) | lower |

Caveats that the source docs make explicit: different devices (SM-S948B vs SM-S938B vs iPhone
SE 3) are **not** cross-comparable; power numbers are model-based estimates; the battery gauge
is quantised. The dashboard labels the device on every bar for exactly this reason.
