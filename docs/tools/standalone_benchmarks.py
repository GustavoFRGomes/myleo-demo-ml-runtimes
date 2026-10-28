#!/usr/bin/env python3
"""Generate docs/standalone-benchmarks.html from the data embedded in docs/benchmarks.html.

Output is one file: inline CSS, inline data, inline JS, no images, no network.
The page is interactive (area tabs, platform/device filters, search, sortable
tables, chart metric switch, row dialog, CSV export) and falls back to static
tables in <noscript>.
"""
import datetime
import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "benchmarks.html"
TARGET = ROOT / "standalone-benchmarks.html"

AREAS = {
    "LLM": {
        "metric": "tokPerSec",
        "metrics": [
            ("tokPerSec", "Decode speed", "tok/s", "higher"),
            ("ttftMs", "Time to first token", "ms", "lower"),
            ("loadMs", "Model load", "ms", "lower"),
            ("powerW", "Average power", "W", "lower"),
            ("mjPerToken", "Energy per token", "mJ", "lower"),
        ],
        "columns": [
            ("Device", "device"), ("Runtime", "runtime"), ("Backend", "backend"),
            ("Model", "model"), ("Load ms", "loadMs"), ("TTFT ms", "ttftMs"),
            ("tok/s", "tokPerSec"), ("W", "powerW"), ("mJ/token", "mjPerToken"),
        ],
    },
    "Vector search": {
        "metric": "msPerChunk",
        "metrics": [
            ("msPerChunk", "Embedding latency", "ms/chunk", "lower"),
            ("loadMs", "Model load", "ms", "lower"),
            ("powerW", "Average power", "W", "lower"),
            ("mjPerChunk", "Energy per chunk", "mJ", "lower"),
        ],
        "columns": [
            ("Device", "device"), ("Runtime", "runtime"), ("Backend", "backend"),
            ("Model", "model"), ("Load ms", "loadMs"), ("ms/chunk", "msPerChunk"),
            ("W", "powerW"), ("mJ/chunk", "mjPerChunk"),
        ],
    },
    "Decision": {
        "metric": "msPerPrompt",
        "metrics": [
            ("msPerPrompt", "Decision latency", "ms/prompt", "lower"),
            ("loadMs", "Model load", "ms", "lower"),
            ("powerW", "Average power", "W", "lower"),
            ("mjPerPrompt", "Energy per decision", "mJ", "lower"),
        ],
        "columns": [
            ("Device", "device"), ("Runtime", "runtime"), ("Backend", "backend"),
            ("Model", "model"), ("Load ms", "loadMs"), ("ms/prompt", "msPerPrompt"),
            ("W", "powerW"), ("mJ/prompt", "mjPerPrompt"),
        ],
    },
}

PLATFORM_ORDER = {"iOS": 0, "Android": 1}
DEVICE_ORDER = {
    "iPhone SE 3": 0, "iPhone 15 Pro": 1, "Galaxy S26 Ultra": 2,
    "Galaxy S25 Ultra": 3, "TCL NXTPaper 70 Pro": 4,
}


def esc(value):
    return html.escape(str(value), quote=True)


def load_rows():
    text = SOURCE.read_text()
    match = re.search(r'<script id="bench-data" type="application/json">\n(.*?)\n</script>', text, re.S)
    if not match:
        raise SystemExit("bench-data block not found in benchmarks.html")
    return json.loads(match.group(1))


def num(value, digits=1):
    if value is None or value == "":
        return "—"
    try:
        number = float(value)
    except (TypeError, ValueError):
        return esc(value)
    if digits == 0:
        return f"{number:,.0f}"
    return f"{number:,.{digits}f}"


def sort_key(row):
    return (
        PLATFORM_ORDER.get(row.get("platform"), 9),
        DEVICE_ORDER.get(row.get("device"), 9),
        row.get("runtime", ""),
        row.get("backend", ""),
    )


def qualifier(row):
    note = row.get("note", "")
    if "2026-10-27" in note:
        return "2026-10-27"
    if "sustained" in note.lower() or "60x" in note:
        return "60-run"
    return ""


def static_table(rows, area):
    """Script-free fallback table (also used for print)."""
    columns = AREAS[area]["columns"] + [("Note", "note")]
    metric = AREAS[area]["metric"]
    _key, _label, _unit, better = next(m for m in AREAS[area]["metrics"] if m[0] == metric)
    sign = -1 if better == "higher" else 1

    def order(row):
        raw = row.get(metric)
        number = float(raw) if isinstance(raw, (int, float)) else float("inf")
        return (row.get("device") or "", sign * number)

    ordered = sorted(rows, key=order)
    span_keys = ("device", "backend", "model")
    spans = {key: [1] * len(ordered) for key in span_keys}
    for key in span_keys:
        index = 0
        while index < len(ordered):
            nxt = index + 1
            while nxt < len(ordered) and (ordered[nxt].get(key) or "") == (ordered[index].get(key) or ""):
                nxt += 1
            spans[key][index] = nxt - index
            for skip in range(index + 1, nxt):
                spans[key][skip] = 0
            index = nxt
    out = ['<div class="table-wrap"><table>', "<thead><tr>"]
    out += [f'<th scope="col">{esc(label)}</th>' for label, _ in columns]
    out.append("</tr></thead><tbody>")
    for position, row in enumerate(ordered):
        out.append("<tr>")
        for label, key in columns:
            if key in span_keys:
                span = spans[key][position]
                if span == 0:
                    continue
                attr = f' rowspan="{span}"' if span > 1 else ""
                out.append(f"<td{attr}>{esc(row.get(key, '') or '')}</td>")
            elif key == "note":
                out.append(f'<td class="note">{esc(row.get(key, ""))}</td>')
            elif isinstance(row.get(key), (int, float)):
                out.append(f'<td class="num">{num(row.get(key), 0 if key == "loadMs" else 1)}</td>')
            else:
                out.append(f"<td>{esc(row.get(key, '') or '')}</td>")
        out.append("</tr>")
    out.append("</tbody></table></div>")
    return "\n".join(out)


def noscript_sections(rows):
    out = []
    for area in AREAS:
        subset = [row for row in rows if row.get("area") == area]
        out.append(f"<section><h2>{esc(area)}</h2>{static_table(subset, area)}</section>")
    return "\n".join(out)


def metric_value(row, key):
    try:
        return float(row.get(key))
    except (TypeError, ValueError):
        return None


def summary_cards(rows):
    """Worst → best range per area × platform; only the best value is bold."""
    cards = []
    for area, config in AREAS.items():
        key = config["metric"]
        _key, _label, unit, better = next(m for m in config["metrics"] if m[0] == key)
        for platform in ("iOS", "Android"):
            subset = [r for r in rows if r["area"] == area and r["platform"] == platform]
            pairs = [(row, metric_value(row, key)) for row in subset]
            pairs = [(row, val) for row, val in pairs if val is not None]
            if not pairs:
                continue
            best_row, best = (min if better == "lower" else max)(pairs, key=lambda pair: pair[1])
            worst_row, worst = (max if better == "lower" else min)(pairs, key=lambda pair: pair[1])
            if best_row is worst_row:
                value = f'<span class="best">{num(best)}</span>'
            else:
                value = (f'<span class="worst">{num(worst)}</span> – '
                         f'<span class="best">{num(best)}</span>')
            cards.append(
                f'<div class="card"><h3>{esc(platform)} · {esc(area)}</h3>'
                f'<div class="big">{value} <span class="unit">{esc(unit)}</span></div>'
                f'<div class="delta">worst: {esc(worst_row["device"])} · {esc(worst_row["runtime"])}</div>'
                f'<div class="delta">best: {esc(best_row["device"])} · {esc(best_row["runtime"])}</div>'
                f"</div>"
            )
    return '<div class="cards">' + "".join(cards) + "</div>"


def build():
    rows = load_rows()
    data_json = json.dumps(rows, indent=1)
    area_meta = json.dumps({k: {kk: vv for kk, vv in v.items()} for k, v in AREAS.items()}, indent=1)
    today = datetime.date.today().isoformat()
    cards = summary_cards(rows)
    tabs = "".join(
        f'<button type="button" role="tab" data-area="{esc(area)}" '
        f'aria-selected="{"true" if index == 0 else "false"}">{esc(area)}</button>'
        for index, area in enumerate(AREAS)
    )

    document = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MyLEO benchmarks — standalone</title>
<style>
  :root {{
    --bg:#ffffff; --fg:#1b1f24; --muted:#5b6673; --line:#d8dee4; --track:#eef2f6;
    --bar:#4a90e2; --best:#1f883d; --card:#f6f8fa; --chip:#eef2f6;
  }}
  @media (prefers-color-scheme: dark) {{
    :root {{ --bg:#0d1117; --fg:#e6edf3; --muted:#9aa7b4; --line:#30363d; --track:#161b22;
             --bar:#58a6ff; --best:#3fb950; --card:#11161d; --chip:#1c232c; }}
  }}
  * {{ box-sizing:border-box; }}
  body {{ margin:0; background:var(--bg); color:var(--fg);
         font:15px/1.45 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }}
  header, main {{ max-width:1100px; margin:0 auto; padding:0 24px; }}
  header {{ padding-top:28px; }}
  h1 {{ margin:0 0 6px; font-size:24px; letter-spacing:-0.01em; }}
  h2 {{ margin:0 0 12px; font-size:18px; }}
  .sub, .foot {{ color:var(--muted); font-size:13px; }}
  .cards {{ display:grid; grid-template-columns:repeat(auto-fit, minmax(230px, 1fr));
            gap:12px; margin:18px 0 14px; }}
  .card {{ border:1px solid var(--line); border-radius:10px; padding:12px 14px; background:var(--card); }}
  .card h3 {{ margin:0 0 6px; font-size:11px; text-transform:uppercase; letter-spacing:.05em; color:var(--muted); }}
  .big {{ font-size:21px; font-weight:400; font-variant-numeric:tabular-nums; white-space:nowrap; }}
  .big .best {{ font-weight:700; }}
  .unit {{ font-size:11.5px; font-weight:500; color:var(--muted); }}
  .delta {{ color:var(--muted); font-size:12px; margin-top:2px; }}
  main {{ padding-bottom:40px; }}
  .tabs {{ display:flex; flex-wrap:wrap; gap:6px; margin:18px 0 12px; }}
  .tabs button {{ font:inherit; background:transparent; color:var(--fg); border:1px solid var(--line);
                  border-radius:999px; padding:6px 14px; cursor:pointer; }}
  .tabs button[aria-selected="true"] {{ background:var(--bar); border-color:var(--bar); color:#fff; font-weight:600; }}
  .controls {{ display:flex; flex-wrap:wrap; gap:10px 14px; align-items:flex-end;
               padding:12px 14px; border:1px solid var(--line); border-radius:10px; background:var(--card); }}
  .controls label {{ display:flex; flex-direction:column; gap:4px; font-size:11px;
                     color:var(--muted); text-transform:uppercase; letter-spacing:.05em; }}
  .controls select, .controls input, .controls button {{
      font:inherit; color:var(--fg); background:var(--bg); border:1px solid var(--line);
      border-radius:8px; padding:6px 9px; }}
  .controls button {{ cursor:pointer; }}
  .controls .grow {{ flex:1 1 180px; }}
  .controls .grow input {{ width:100%; }}
  .count {{ color:var(--muted); font-size:12px; margin-left:auto; padding-bottom:7px; }}
  .chart {{ border:1px solid var(--line); border-radius:10px; padding:12px 14px;
            display:grid; grid-template-columns:minmax(190px, 34%) 1fr 74px;
            gap:3px 12px; align-items:center; }}
  .chart-btn {{ display:contents; border:0; background:none; padding:0; cursor:pointer; color:inherit; font:inherit; }}
  .chart-btn:focus-visible .chart-label {{ outline:2px solid var(--bar); outline-offset:2px; }}
  .chart-label {{ font-size:12px; text-align:right; overflow:hidden; text-overflow:ellipsis;
                  white-space:nowrap; }}
  .chart-btn:hover .chart-label {{ color:var(--bar); }}
  .chart-value {{ text-align:right; font-variant-numeric:tabular-nums; font-size:12px; font-weight:600; }}
  progress {{ width:100%; height:14px; appearance:none; border:0; border-radius:6px; background:var(--track); }}
  progress::-webkit-progress-bar {{ background:var(--track); border-radius:6px; }}
  progress::-webkit-progress-value {{ background:var(--bar); border-radius:6px; }}
  progress::-moz-progress-bar {{ background:var(--bar); border-radius:6px; }}
  progress.best::-webkit-progress-value {{ background:var(--best); }}
  progress.best::-moz-progress-bar {{ background:var(--best); }}
  .table-wrap {{ margin-top:14px; overflow:auto; border:1px solid var(--line); border-radius:10px; }}
  table {{ width:100%; border-collapse:collapse; font-size:12.5px; }}
  th, td {{ text-align:left; padding:7px 9px; border-bottom:1px solid var(--line); vertical-align:top; }}
  th {{ color:var(--muted); font-weight:600; background:var(--bg); position:sticky; top:0; }}
  th button {{ all:unset; cursor:pointer; color:inherit; font:inherit; }}
  th button:focus-visible {{ outline:2px solid var(--bar); }}
  td.num {{ text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }}
  td.note {{ color:var(--muted); font-size:12px; }}
  td:first-child {{ white-space:nowrap; }}
  tr.row-button {{ cursor:pointer; }}
  tr.row-button:hover {{ background:var(--chip); }}
  details {{ border:1px solid var(--line); border-radius:10px; padding:10px 14px; margin-top:16px; }}
  summary {{ cursor:pointer; font-weight:600; }}
  details p, details li {{ color:var(--muted); font-size:13px; }}
  dialog {{ border:1px solid var(--line); border-radius:12px; padding:0; max-width:620px; width:92%;
            background:var(--bg); color:var(--fg); }}
  dialog::backdrop {{ background:rgba(0,0,0,.45); }}
  .dialog-head {{ display:flex; justify-content:space-between; align-items:center; gap:12px;
                  padding:14px 16px; border-bottom:1px solid var(--line); }}
  .dialog-head h3 {{ margin:0; font-size:15px; }}
  .dialog-body {{ padding:12px 16px 18px; }}
  .dialog-body dl {{ display:grid; grid-template-columns:auto 1fr; gap:6px 16px; margin:0; font-size:13px; }}
  .dialog-body dt {{ color:var(--muted); }}
  .dialog-body dd {{ margin:0; font-variant-numeric:tabular-nums; }}
  .pill {{ display:inline-block; padding:1px 8px; border-radius:999px; background:var(--chip);
           font-size:11px; color:var(--muted); margin-left:6px; }}
  .actions {{ display:flex; gap:8px; }}
  .actions button {{ font:inherit; border:1px solid var(--line); background:var(--bg); color:var(--fg);
                     border-radius:8px; padding:5px 10px; cursor:pointer; }}
  #close {{ font-size:22px; line-height:1; border:1px solid var(--line); background:var(--bg);
            color:var(--fg); border-radius:8px; width:34px; height:34px; cursor:pointer; }}
  .foot {{ margin-top:24px; }}
  @media print {{
    .tabs, .controls, .actions {{ display:none; }}
    body {{ background:#fff; color:#000; }}
    .chart, .table-wrap, details, dialog {{ border-color:#bbb; }}
    progress {{ border:1px solid #999; background:#eee; }}
    progress::-webkit-progress-value {{ background:#444; }}
    progress::-moz-progress-bar {{ background:#444; }}
    section {{ break-inside:avoid; }}
  }}
</style>
</head>
<body>
<header>
  <h1>MyLEO benchmarks — standalone</h1>
  <p class="sub">One file: inline styles, data and script; no images, no network, no siblings.
  Tab an area, filter, search, sort a column or open a bar for the full row.</p>
</header>
<main>
  <noscript><style>#interactive {{ display:none; }}</style></noscript>
  <div id="interactive">
  {cards}
  <div class="tabs" role="tablist" aria-label="Benchmark area">{tabs}</div>

  <section class="controls" aria-label="Filters">
    <label>Platform
      <select id="platform">
        <option value="all">All</option>
        <option value="iOS">iOS</option>
        <option value="Android">Android</option>
      </select>
    </label>
    <label>Device<select id="device"><option value="all">All devices</option></select></label>
    <label>Chart metric<select id="metric"></select></label>
    <label class="grow">Search<input id="query" type="search" placeholder="device, runtime, model, note…"></label>
    <output class="count" id="count" for="platform device metric query"></output>
    <div class="actions"><button type="button" id="export">Download CSV</button></div>
  </section>

  <section id="chart-section" aria-live="polite">
    <h2 id="chart-title"></h2>
    <div class="chart" id="chart"></div>
  </section>

  <section>
    <h2>All matching rows</h2>
    <div class="table-wrap" id="table-wrap"><table id="table"></table></div>
  </section>
  </div>

  <details>
    <summary>Device specs — SoC, RAM, OS</summary>
    <div class="table-wrap" style="margin:10px 0">
      <table>
        <thead><tr><th>Device</th><th>Model code</th><th>SoC</th><th>RAM</th><th>OS</th></tr></thead>
        <tbody>
          <tr><td>iPhone SE 3</td><td>iPhone14,6</td><td>Apple A15 Bionic</td><td>4 GB</td><td>iOS 27.0.1 (24A446)</td></tr>
          <tr><td>iPhone 15 Pro</td><td>iPhone16,1</td><td>Apple A17 Pro</td><td>8 GB</td><td>iOS 27.0.1 (24A446)</td></tr>
          <tr><td>Galaxy S26 Ultra</td><td>SM-S948B</td><td>Snapdragon 8 Elite Gen 5 for Galaxy</td><td>12 GB</td><td>Android 17</td></tr>
          <tr><td>Galaxy S25 Ultra</td><td>SM-S938B</td><td>Snapdragon 8 Elite (SM8750)</td><td>12 GB</td><td>Android 17</td></tr>
          <tr><td>TCL NXTPaper 70 Pro</td><td>T807D</td><td>MediaTek Dimensity 7300 (MT6878)</td><td>8 GB</td><td>Android 16</td></tr>
        </tbody>
      </table>
    </div>
    <p>RAM is nominal; kernels report less (11.4 GB usable on the S25 Ultra, 7.3 GB on the TCL).</p>
  </details>
  <details>
    <summary>Methodology</summary>
    <p>LLM: MiniCPM5-1B or Phi-4-mini int4, one turn, temperature 0.8, top-k 40, top-p 0.95,
    512-token cap (the ONNX GenAI engine allows up to 1536 total when EOS does not fire).
    Vector search: MiniLM-L6-v2, 384-d, 20-chunk corpus. Decision: Julia-1, 18-prompt suite.</p>
    <p>Energy is an estimate: iOS uses the ~5% gauge or Power Profiler; Android integrates
    current × voltage at 1 Hz. Compare lanes on the same device, not across devices.</p>
  </details>
  <details>
    <summary>Notes &amp; quirks</summary>
    <ul>
      <li>iOS Core ML runs the decision leaf is reserved — no Julia-1 package exists yet.</li>
      <li>The LiteRT decision GPU delegate fails to compile on the 15 Pro; on the TCL it
      compiles but is 7.6× slower than ONNX CPU.</li>
      <li>Rows tagged <span class="pill">60-run</span> or <span class="pill">2026-10-27</span>
      are sustained battery runs, not cold single runs.</li>
    </ul>
  </details>

  <noscript>
    <p class="sub">JavaScript is off — showing the static tables instead.</p>
    {noscript_sections(rows)}
  </noscript>

  <p class="foot" id="foot">Generated {today} from the <code>bench-data</code> block of
  <code>benchmarks.html</code> ({len(rows)} rows). Single file, no external assets.</p>
  <p class="foot">Source data and code:
  <a href="https://github.com/GustavoFRGomes/myleo-demo-ml-runtimes">github.com/GustavoFRGomes/myleo-demo-ml-runtimes</a> — the page itself needs nothing but this
  file; the link is only for the underlying data.</p>
</main>

<dialog id="detail" aria-labelledby="detail-title">
  <div class="dialog-head">
    <h3 id="detail-title">Row</h3>
    <button type="button" id="close" aria-label="Close">&times;</button>
  </div>
  <div class="dialog-body"><dl id="detail-fields"></dl></div>
</dialog>

<script id="bench-data-LOCAL" type="application/json">
{data_json}
</script>
<script>
(() => {{
  "use strict";
  const DATA = JSON.parse(document.getElementById("bench-data-LOCAL").textContent);
  const AREAS = {area_meta};
  const state = {{ area: "LLM", platform: "all", device: "all", query: "", metric: null, sortKey: null, sortDir: -1 }};
  const $ = (id) => document.getElementById(id);

  const qualifier = (row) => {{
    const note = (row.note || "").toLowerCase();
    if (note.includes("2026-10-27")) return "2026-10-27";
    if (note.includes("sustained") || note.includes("60x")) return "60-run";
    return "";
  }};

  const value = (row, key) => {{
    const n = Number(row[key]);
    return Number.isFinite(n) ? n : null;
  }};

  const fmt = (v, d = 1) => (v === null || v === undefined || v === "" ? "—" : Number(v).toFixed(d).replace(/\\.0$/, ""));

  function filtered() {{
    const q = state.query.trim().toLowerCase();
    return DATA.filter((row) => row.area === state.area)
      .filter((row) => state.platform === "all" || row.platform === state.platform)
      .filter((row) => state.device === "all" || row.device === state.device)
      .filter((row) => !q || ["device","runtime","backend","model","note"].some(
        (key) => String(row[key] || "").toLowerCase().includes(q)));
  }}

  const metricDef = () => AREAS[state.area].metrics.find((m) => m[0] === (state.metric || AREAS[state.area].metric));

  function renderMetrics() {{
    const select = $("metric");
    select.innerHTML = AREAS[state.area].metrics
      .map(([key, label, unit]) => `<option value="${{key}}">${{label}} (${{unit}})</option>`).join("");
    select.value = state.metric || AREAS[state.area].metric;
  }}

  function renderDevices() {{
    const devices = [...new Set(DATA.filter((r) => r.area === state.area)
      .filter((r) => state.platform === "all" || r.platform === state.platform)
      .map((r) => r.device))];
    devices.sort();
    const select = $("device");
    select.innerHTML = '<option value="all">All devices</option>' +
      devices.map((d) => `<option value="${{d}}">${{d}}</option>`).join("");
    select.value = devices.includes(state.device) ? state.device : "all";
    state.device = select.value;
  }}

  function renderChart(rows) {{
    const [key, label, unit, better] = metricDef();
    const present = rows.map((r) => [r, value(r, key)]).filter((pair) => pair[1] !== null);
    $("chart-title").textContent = `${{state.area}} — ${{label}} (${{unit}}, ${{better}} is better)`;
    if (!present.length) {{
      $("chart").innerHTML = '<p class="sub">No measurements for this filter.</p>';
      return;
    }}
    const ordered = present.slice().sort((a, b) => better === "lower" ? a[1] - b[1] : b[1] - a[1]);
    const best = ordered[0][0];
    const peak = Math.max(...present.map((p) => p[1]));
    $("chart").innerHTML = ordered.map(([row, v]) => {{
      const tag = qualifier(row);
      const text = [row.device, row.runtime, row.backend].filter(Boolean).join(" · ") + (tag ? ` · ${{tag}}` : "");
      return `<button type="button" class="chart-btn" data-id="${{DATA.indexOf(row)}}">
        <span class="chart-label" title="${{text.replace(/"/g, "&quot;")}}">${{text}}</span>
        <progress max="${{peak}}" value="${{v}}" class="${{row === best ? "best" : ""}}"></progress>
        <span class="chart-value">${{fmt(v)}}</span>
      </button>`;
    }}).join("");
  }}

  function renderTable(rows) {{
    const columns = AREAS[state.area].columns;
    const sortKey = state.sortKey || (state.metric || AREAS[state.area].metric);
    const dir = state.sortDir;
    // Tables group by device so repeated cells can be merged; the metric orders rows
    // inside each device. Text-column sorts stay global.
    const textKey = ["device", "runtime", "backend", "model"].includes(sortKey);
    const sorted = rows.slice().sort((a, b) => {{
      if (textKey) {{
        const av = String(a[sortKey] || ""), bv = String(b[sortKey] || "");
        return (av > bv ? 1 : av < bv ? -1 : 0) * dir;
      }}
      const device = String(a.device || "").localeCompare(String(b.device || ""));
      if (device) return device;
      const av = value(a, sortKey) ?? -Infinity;
      const bv = value(b, sortKey) ?? -Infinity;
      return (av > bv ? 1 : av < bv ? -1 : 0) * dir;
    }});
    const head = columns.map(([label, key]) =>
      `<th scope="col"><button type="button" data-sort="${{key}}">${{label}}${{state.sortKey === key ? (dir > 0 ? " ▲" : " ▼") : ""}}</button></th>`
    ).join("") + '<th scope="col">Note</th>';
    // Collapse repeated Device/Backend/Model cells into rowspans.
    const spanKeys = ["device", "backend", "model"];
    const spans = sorted.map(() => ({{}}));
    for (const key of spanKeys) {{
      let index = 0;
      while (index < sorted.length) {{
        let next = index + 1;
        while (next < sorted.length && String(sorted[next][key] ?? "") === String(sorted[index][key] ?? "")) next++;
        spans[index][key] = next - index;
        for (let k = index + 1; k < next; k++) spans[k][key] = 0;
        index = next;
      }}
    }}
    const body = sorted.map((row, index) => {{
      const cells = columns.map(([, key]) => {{
        if (spanKeys.includes(key)) {{
          const span = spans[index][key];
          if (span === 0) return "";
          return `<td${{span > 1 ? ` rowspan="${{span}}"` : ""}}>${{String(row[key] ?? "")}}</td>`;
        }}
        return typeof row[key] === "number"
          ? `<td class="num">${{fmt(row[key], key === "loadMs" ? 0 : 1)}}</td>`
          : `<td>${{String(row[key] ?? "")}}</td>`;
      }}).join("");
      return `<tr class="row-button" data-id="${{DATA.indexOf(row)}}">${{cells}}<td class="note">${{row.note || ""}}</td></tr>`;
    }}).join("");
    $("table").innerHTML = `<thead><tr>${{head}}</tr></thead><tbody>${{body}}</tbody>`;
  }}

  function render() {{
    renderMetrics();
    renderDevices();
    const rows = filtered();
    $("count").textContent = `${{rows.length}} row${{rows.length === 1 ? "" : "s"}}`;
    renderChart(rows);
    renderTable(rows);
    if (location.hash.slice(1) !== state.area) history.replaceState(null, "", `#${{encodeURIComponent(state.area)}}`);
  }}

  const LABELS = {{
    platform: "Platform", area: "Area", device: "Device", runtime: "Runtime", backend: "Backend",
    model: "Model", loadMs: "Load (ms)", ttftMs: "TTFT (ms)", tokPerSec: "Decode (tok/s)",
    powerW: "Power (W)", mjPerToken: "Energy (mJ/token)", msPerChunk: "Embedding (ms/chunk)",
    mjPerChunk: "Energy (mJ/chunk)", msPerPrompt: "Decision (ms/prompt)",
    mjPerPrompt: "Energy (mJ/decision)", pssMb: "Memory (MB PSS)", note: "Note",
  }};
  const ORDER = ["device", "platform", "area", "runtime", "backend", "model", "loadMs",
    "ttftMs", "tokPerSec", "powerW", "mjPerToken", "msPerChunk", "mjPerChunk",
    "msPerPrompt", "mjPerPrompt", "pssMb", "note"];

  function openDetail(id) {{
    const row = DATA[id];
    if (!row) return;
    $("detail-title").textContent = `${{row.device}} · ${{row.runtime}}`;
    const keys = ORDER.filter((k) => row[k] !== null && row[k] !== undefined && row[k] !== "")
      .concat(Object.keys(row).filter((k) => !ORDER.includes(k) && row[k] !== null && row[k] !== undefined && row[k] !== ""));
    $("detail-fields").innerHTML = keys.map((k) => {{
      const v = row[k];
      const shown = typeof v === "number" ? fmt(v, Number.isInteger(v) ? 0 : 1) : String(v);
      return `<dt>${{LABELS[k] || k}}</dt><dd>${{shown}}</dd>`;
    }}).join("");
    $("detail").showModal();
  }}

  function toCsv(rows) {{
    const keys = ["platform","area","device","runtime","backend","model","loadMs","ttftMs","tokPerSec",
      "powerW","mjPerToken","msPerChunk","mjPerChunk","msPerPrompt","mjPerPrompt","note"];
    const escape = (v) => `"${{String(v ?? "").replace(/"/g, '""')}}"`;
    return [keys.join(","), ...rows.map((r) => keys.map((k) => escape(r[k])).join(","))].join("\\n");
  }}

  document.addEventListener("click", (event) => {{
    const tab = event.target.closest(".tabs button");
    if (tab) {{
      state.area = tab.dataset.area;
      state.metric = null;
      state.sortKey = AREAS[state.area].metric;
      state.sortDir = -1;
      state.device = "all";
      document.querySelectorAll(".tabs button").forEach((b) => b.setAttribute("aria-selected", String(b === tab)));
      render();
      return;
    }}
    const sort = event.target.closest("th button");
    if (sort) {{
      const key = sort.dataset.sort;
      state.sortDir = state.sortKey === key ? -state.sortDir : -1;
      state.sortKey = key;
      renderTable(filtered());
      return;
    }}
    const hit = event.target.closest("[data-id]");
    if (hit) {{
      openDetail(Number(hit.dataset.id));
      return;
    }}
    if (event.target.id === "close") $("detail").close();
  }});

  $("detail").addEventListener("click", (event) => {{
    if (event.target === $("detail")) $("detail").close();
  }});

  $("platform").addEventListener("change", (event) => {{
    state.platform = event.target.value;
    state.device = "all";
    render();
  }});
  $("device").addEventListener("change", (event) => {{ state.device = event.target.value; render(); }});
  $("metric").addEventListener("change", (event) => {{
    state.metric = event.target.value;
    state.sortKey = null;
    renderChart(filtered());
    renderTable(filtered());
  }});
  $("query").addEventListener("input", (event) => {{ state.query = event.target.value; render(); }});
  $("export").addEventListener("click", () => {{
    const blob = new Blob([toCsv(filtered())], {{ type: "text/csv" }});
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = "myleo-benchmarks.csv";
    link.click();
    URL.revokeObjectURL(link.href);
  }});

  const initial = decodeURIComponent(location.hash.slice(1));
  if (AREAS[initial]) {{
    state.area = initial;
    document.querySelectorAll(".tabs button").forEach((b) =>
      b.setAttribute("aria-selected", String(b.dataset.area === initial)));
  }}
  state.sortKey = AREAS[state.area].metric;
  document.body.dataset.ready = "1";
  render();
}})();
</script>
</body>
</html>
"""
    TARGET.write_text(document)
    print(f"wrote {TARGET} ({len(rows)} rows, {TARGET.stat().st_size} bytes)")


if __name__ == "__main__":
    build()
