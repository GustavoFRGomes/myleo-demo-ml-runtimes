#!/usr/bin/env python3
"""Generate docs/standalone-benchmarks.html from the data embedded in docs/benchmarks.html.

The output is a single self-contained file: inline CSS, no scripts, no images, no
network access. It is a static snapshot of the interactive dashboard.
"""
import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "benchmarks.html"
TARGET = ROOT / "standalone-benchmarks.html"

AREAS = [
    ("LLM", "Decode speed", "tok/s", "higher", "tokPerSec"),
    ("Vector search", "Embedding latency", "ms/chunk", "lower", "msPerChunk"),
    ("Decision", "Decision latency", "ms/prompt", "lower", "msPerPrompt"),
]

PLATFORM_ORDER = {"iOS": 0, "Android": 1}
DEVICE_ORDER = {"iPhone SE 3": 0, "iPhone 15 Pro": 1, "SM-S948B": 2, "SM-S938B": 3, "TCL T807D": 4}


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
    """Short tag that keeps repeated device/runtime labels distinguishable."""
    note = row.get("note", "")
    if "2026-10-27" in note:
        return "2026-10-27"
    if "sustained" in note.lower() or "60x" in note:
        return "60-run"
    return ""


def metric_value(row, key):
    value = row.get(key)
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def bar_chart(rows, key, better):
    values = [(row, metric_value(row, key)) for row in rows]
    values = [(row, value) for row, value in values if value is not None]
    if not values:
        return '<p class="empty">No measurements.</p>'
    peak = max(value for _, value in values)
    best = min(values, key=lambda item: item[1]) if better == "lower" else max(values, key=lambda item: item[1])
    out = ['<div class="chart">']
    for row, value in values:
        width = max(1.0, value / peak * 100.0)
        label = f'{row.get("device", "")} · {row.get("runtime", "")}'
        backend = row.get("backend", "")
        if backend:
            label += f' · {backend}'
        tag = qualifier(row)
        if tag:
            label += f" · {tag}"
        klass = "bar best" if row is best[0] else "bar"
        out.append(
            f'<div class="chart-row" title="{esc(label)}">'
            f'<div class="chart-label">{esc(label)}</div>'
            f'<div class="chart-track"><div class="{klass}" style="width:{width:.1f}%"></div></div>'
            f'<div class="chart-value">{num(value)}</div>'
            f"</div>"
        )
    out.append("</div>")
    return "\n".join(out)


def table(rows, area):
    if area == "LLM":
        head = ["Device", "Runtime", "Backend", "Model", "Load ms", "TTFT ms", "tok/s", "W", "mJ/token", "Note"]
        cells = lambda r: [
            r.get("device", ""), r.get("runtime", ""), r.get("backend", ""), r.get("model", ""),
            num(r.get("loadMs"), 0), num(r.get("ttftMs"), 0), num(r.get("tokPerSec")),
            num(r.get("powerW")), num(r.get("mjPerToken")), r.get("note", ""),
        ]
    elif area == "Vector search":
        head = ["Device", "Runtime", "Backend", "Model", "Load ms", "ms/chunk", "W", "mJ/chunk", "Note"]
        cells = lambda r: [
            r.get("device", ""), r.get("runtime", ""), r.get("backend", ""), r.get("model", ""),
            num(r.get("loadMs"), 0), num(r.get("msPerChunk")),
            num(r.get("powerW")), num(r.get("mjPerChunk")), r.get("note", ""),
        ]
    else:
        head = ["Device", "Runtime", "Backend", "Model", "Load ms", "ms/prompt", "W", "mJ/prompt", "Note"]
        cells = lambda r: [
            r.get("device", ""), r.get("runtime", ""), r.get("backend", ""), r.get("model", ""),
            num(r.get("loadMs"), 0), num(r.get("msPerPrompt")),
            num(r.get("powerW")), num(r.get("mjPerPrompt")), r.get("note", ""),
        ]
    out = ['<div class="table-wrap"><table>', "<thead><tr>"]
    out += [f"<th>{esc(h)}</th>" for h in head]
    out.append("</tr></thead><tbody>")
    for row in sorted(rows, key=sort_key):
        values = cells(row)
        out.append("<tr>")
        for index, value in enumerate(values):
            klass = ' class="num"' if 4 <= index <= len(head) - 2 else ' class="note"'
            out.append(f"<td{klass}>{num(value) if isinstance(value, (int, float)) else esc(value)}</td>")
        out.append("</tr>")
    out.append("</tbody></table></div>")
    return "\n".join(out)


def summary_cards(rows):
    cards = []
    for area, title, unit, better, key in AREAS:
        for platform in ("iOS", "Android"):
            subset = [r for r in rows if r.get("area") == area and r.get("platform") == platform]
            pairs = [(r, metric_value(r, key)) for r in subset]
            pairs = [(r, v) for r, v in pairs if v is not None]
            if not pairs:
                continue
            winner, value = (min(pairs, key=lambda p: p[1]) if better == "lower" else max(pairs, key=lambda p: p[1]))
            cards.append(
                f'<div class="card"><h3>{esc(platform)} · {esc(area)}</h3>'
                f'<div class="big">{num(value)} <span class="unit">{esc(unit)}</span></div>'
                f'<div class="delta">{esc(winner.get("device", ""))} · {esc(winner.get("runtime", ""))}</div></div>'
            )
    return '<div class="cards">' + "\n".join(cards) + "</div>"


def build():
    rows = load_rows()
    sections = []
    toc = []
    for area, title, unit, better, key in AREAS:
        subset = [r for r in rows if r.get("area") == area]
        if not subset:
            continue
        anchor = area.lower().replace(" ", "-")
        toc.append(f'<a href="#{anchor}">{esc(area)}</a>')
        sections.append(
            f'<section id="{anchor}"><h2>{esc(area)} — {esc(title)} ({esc(unit)}, {better} is better)</h2>'
            f"{bar_chart(subset, key, better)}{table(subset, area)}</section>"
        )

    generated = "2026-10-27"
    document = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MyLEO benchmarks — standalone snapshot</title>
<style>
  :root {{
    --bg:#ffffff; --fg:#1b1f24; --muted:#5b6673; --line:#d8dee4;
    --track:#eef2f6; --bar:#4a90e2; --best:#1f883d; --card:#f6f8fa;
  }}
  @media (prefers-color-scheme: dark) {{
    :root {{ --bg:#0d1117; --fg:#e6edf3; --muted:#9aa7b4; --line:#30363d;
             --track:#161b22; --bar:#58a6ff; --best:#3fb950; --card:#11161d; }}
  }}
  * {{ box-sizing:border-box; }}
  body {{ margin:0; background:var(--bg); color:var(--fg);
         font:15px/1.45 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }}
  header, main {{ max-width:1100px; margin:0 auto; padding:0 24px; }}
  header {{ padding-top:30px; }}
  h1 {{ margin:0 0 6px; font-size:24px; letter-spacing:-0.01em; }}
  h2 {{ margin:0 0 14px; font-size:18px; }}
  .sub, .foot {{ color:var(--muted); font-size:13px; }}
  section {{ margin-top:34px; }}
  .cards {{ display:grid; grid-template-columns:repeat(auto-fit, minmax(220px, 1fr)); gap:12px; margin-top:22px; }}
  .card {{ border:1px solid var(--line); border-radius:10px; padding:12px 14px; background:var(--card); }}
  .card h3 {{ margin:0 0 6px; font-size:11px; text-transform:uppercase; letter-spacing:.05em; color:var(--muted); }}
  .big {{ font-size:26px; font-weight:700; }}
  .unit {{ font-size:13px; font-weight:500; color:var(--muted); }}
  .delta {{ color:var(--muted); font-size:12px; margin-top:2px; }}
  .chart {{ border:1px solid var(--line); border-radius:10px; padding:12px 14px; }}
  .chart-row {{ display:grid; grid-template-columns:minmax(180px, 34%) 1fr 70px; gap:12px; align-items:center; padding:4px 0; }}
  .chart-label {{ font-size:12px; color:var(--fg); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }}
  .chart-track {{ background:var(--track); border-radius:6px; height:14px; overflow:hidden; }}
  .bar {{ height:100%; background:var(--bar); border-radius:6px; }}
  .bar.best {{ background:var(--best); }}
  .chart-value {{ text-align:right; font-variant-numeric:tabular-nums; font-size:12px; font-weight:600; }}
  .table-wrap {{ margin-top:14px; overflow:auto; border:1px solid var(--line); border-radius:10px; }}
  table {{ width:100%; border-collapse:collapse; font-size:12.5px; }}
  th, td {{ text-align:left; padding:7px 9px; border-bottom:1px solid var(--line); vertical-align:top; }}
  th {{ color:var(--muted); font-weight:600; background:var(--bg); }}
  td.num {{ text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }}
  td.note {{ color:var(--muted); font-size:12px; }}
  td:first-child {{ white-space:nowrap; }}
  .empty {{ color:var(--muted); }}
  .toc {{ margin-top:22px; font-size:13px; }}
  .toc a {{ color:var(--bar); text-decoration:none; }}
  .toc a:hover {{ text-decoration:underline; }}
  .foot {{ margin:30px 0 40px; }}
  @media print {{
    body {{ background:#fff; color:#000; }}
    .card, .chart, .table-wrap {{ border-color:#bbb; }}
    .bar {{ background:#666 !important; }}
    .bar.best {{ background:#000 !important; }}
    section {{ break-inside:avoid; }}
  }}
</style>
</head>
<body>
<header>
  <h1>MyLEO benchmarks — standalone snapshot</h1>
  <p class="sub">Single self-contained file: inline styles, no scripts, no images, no network.
  Generated {generated} from the data embedded in <code>benchmarks.html</code> ({len(rows)} measured rows).</p>
</header>
<main>
  {summary_cards(rows)}
  <nav class="toc">{' · '.join(toc)}</nav>
  {''.join(sections)}
  <p class="foot">Methods: LLM = MiniCPM5-1B / Phi-4-mini int4, one turn, temperature 0.8, top-k 40,
  top-p 0.95, 512-token cap (the ONNX GenAI engine allows up to 1536 total when EOS does not fire).
  Vector search = MiniLM-L6-v2, 384-d, 20-chunk corpus. Decision = Julia-1, 18-prompt suite.
  Energy is an estimate: iOS uses the ~5% gauge or Power Profiler; Android integrates
  current × voltage. Compare lanes on the same device, not across devices.</p>
</main>
</body>
</html>
"""
    TARGET.write_text(document)
    print(f"wrote {TARGET} ({len(rows)} rows, {TARGET.stat().st_size} bytes)")


if __name__ == "__main__":
    build()
