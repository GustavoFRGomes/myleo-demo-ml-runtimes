# Standalone Benchmarks Page

`docs/standalone-benchmarks.html` is the one-file version of the benchmarks dashboard: inline
CSS, inline data and inline JS, with **no images, no CSV/PNG siblings and no network access**.
It exists for places where only a single attachment can travel — email, chat, a Confluence HTML
macro, or any host that strips scripts. Open it directly:

```bash
open docs/standalone-benchmarks.html      # macOS, works from file://
```

It is generated, never hand-edited. The source of truth is the
`<script id="bench-data" type="application/json">` block in `docs/benchmarks.html`.

## What it contains

- 6 summary cards: worst → best range per area × platform (only the best value is bold),
  with the two device/runtime labels underneath.
- **Device specs** (collapsible): model code, SoC, RAM and OS for every device in the data.
- Three sections — **LLM** (tok/s), **Vector search** (ms/chunk), **Decision** (ms/prompt) —
  each with a bar chart and a full table including load, power (W) and energy (mJ per
  op) where measured.
- Row count as of 2026-10-28: **44** (16 LLM, 18 vector, 10 decision).
- Footer links back to the source repository (the only outbound link; nothing is fetched when
  the page loads).

## Interaction

| Control | Behaviour |
|---|---|
| Area tabs | Switches the section; the URL hash follows (`#LLM`, `#Vector%20search`, `#Decision`) so a tab can be linked |
| Platform / Device | Filter the active area; the device list is rebuilt per platform |
| Chart metric | Decode tok/s, TTFT, load, power or energy; best row is green |
| Search | Matches device, runtime, backend, model and note text |
| Chart bar or table row | Opens a native `<dialog>` with every field of that row, human-labelled |
| Table header | Sorts by that column; numeric columns default to best-first |
| Download CSV | Exports the current filtered rows (same column set as `benchmarks-data.csv`) |
| Device specs / Methodology / Notes | `<details>` panels (specs are static, so they also work without JS) |
| `<noscript>` | Hides the interactive shell and renders all rows as three static tables |

## Regenerate

After editing the dashboard data (see *Update the numbers* in
[`benchmarks-dashboard.md`](benchmarks-dashboard.md)):

```bash
python3 docs/tools/standalone_benchmarks.py
```

The generator reads `bench-data` from `docs/benchmarks.html` and writes
`docs/standalone-benchmarks.html`. It is deterministic: running it twice produces byte-identical
output. `AGENTS.md` lists it as the fourth dashboard-sync step.

## Verify

```bash
# no external dependencies (the only URL is the footer anchor)
grep -cE '<link|<img|<script[^>]+src=' docs/standalone-benchmarks.html   # expect 0

# row count matches the dashboard
python3 - <<'PY'
import json, re
html = open("docs/benchmarks.html").read()
rows = json.loads(re.search(r'id="bench-data" type="application/json">\n(.*?)\n</script>', html, re.S).group(1))
print(len(rows), {a: sum(r["area"] == a for r in rows) for a in ("LLM", "Vector search", "Decision")})
PY
```

Then open the page and check the tab counts against that output (currently 16 / 18 / 10, 44
total). With scripts disabled it should show 44 data rows in three tables.

## Limits

- Same measurement caveats as the dashboard: energy is an estimate, and rows from different
  devices are not directly comparable — use the device column.
- With JS enabled, **print** renders the active area only; use the `<noscript>` tables (or the
  dashboard's CSV) when a full printable dump is needed.
- The dialog lists every raw field, so it shows more than the table (including fields not
  plotted for that area).

## Files

| File | Role |
|---|---|
| `docs/standalone-benchmarks.html` | The generated single-file page |
| `docs/tools/standalone_benchmarks.py` | Generator (reads `bench-data`, writes the page) |
| `docs/benchmarks.html` | Interactive dashboard and data source |
| `docs/benchmarks-data.csv` | CSV export of the same rows (not needed by the standalone page) |
| `docs/benchmarks-dashboard.png` | Dashboard screenshot (not needed by the standalone page) |
