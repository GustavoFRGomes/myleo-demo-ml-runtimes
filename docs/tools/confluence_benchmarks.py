#!/usr/bin/env python3
"""Generate docs/confluence-benchmarks.html — a fragment for Confluence's HTML macro.

Differences from docs/standalone-benchmarks.html, and why:

* no doctype/html/head/body — macros insert the fragment into the page's own document;
* no <script> — Cloud strips it, DC may sandbox it;
* no <style> block — many macros drop it, so every rule is an inline style attribute;
* no <dialog>/<progress>/<details> — macros may unwrap them, so the layout is plain
  div/table with headings;
* no external assets — the only URL is the source-repo anchor in the footer.

The data still comes from the bench-data block in docs/benchmarks.html.
"""
import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "benchmarks.html"
TARGET = ROOT / "confluence-benchmarks.html"

REPO = "https://github.com/GustavoFRGomes/myleo-demo-ml-runtimes"

AREAS = [
    ("LLM", "Decode speed", "tok/s", "desc", "tokPerSec",
     [("Device", "device"), ("Runtime", "runtime"), ("Backend", "backend"),
      ("Model", "model"), ("Load ms", "loadMs"), ("TTFT ms", "ttftMs"),
      ("tok/s", "tokPerSec"), ("W", "powerW"), ("mJ/token", "mjPerToken")]),
    ("Vector search", "Embedding latency", "ms/chunk", "asc", "msPerChunk",
     [("Device", "device"), ("Runtime", "runtime"), ("Backend", "backend"),
      ("Model", "model"), ("Load ms", "loadMs"), ("ms/chunk", "msPerChunk"),
      ("W", "powerW"), ("mJ/chunk", "mjPerChunk")]),
    ("Decision", "Decision latency", "ms/prompt", "asc", "msPerPrompt",
     [("Device", "device"), ("Runtime", "runtime"), ("Backend", "backend"),
      ("Model", "model"), ("Load ms", "loadMs"), ("ms/prompt", "msPerPrompt"),
      ("W", "powerW"), ("mJ/prompt", "mjPerPrompt")]),
]

SPECS = [
    ("iPhone SE 3", "iPhone14,6", "Apple A15 Bionic", "4 GB", "iOS 27.0.1 (24A446)"),
    ("iPhone 15 Pro", "iPhone16,1", "Apple A17 Pro", "8 GB", "iOS 27.0.1 (24A446)"),
    ("Galaxy S26 Ultra", "SM-S948B", "Snapdragon 8 Elite Gen 5 for Galaxy", "12 GB", "Android 17"),
    ("Galaxy S25 Ultra", "SM-S938B", "Snapdragon 8 Elite (SM8750)", "12 GB", "Android 17"),
    ("TCL NXTPaper 70 Pro", "T807D", "MediaTek Dimensity 7300 (MT6878)", "8 GB", "Android 16"),
]

STYLE = {
    "wrap": "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;"
            "color:#1b1f24;line-height:1.45;font-size:14px;",
    "h2": "font-size:18px;margin:26px 0 8px;",
    "h3": "font-size:14px;margin:20px 0 6px;",
    "note": "color:#5b6673;font-size:12px;margin:0 0 10px;",
    "table": "width:100%;border-collapse:collapse;font-size:12.5px;margin:6px 0 14px;",
    "th": "text-align:left;padding:6px 8px;border-bottom:1px solid #d8dee4;color:#5b6673;"
          "font-weight:600;background:#f6f8fa;",
    "td": "padding:6px 8px;border-bottom:1px solid #eef2f6;vertical-align:top;",
    "num": "text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap;",
    "note-cell": "color:#5b6673;font-size:12px;",
    "best": "font-weight:700;color:#1f883d;",
    "bar-track": "background:#eef2f6;border-radius:6px;height:10px;width:100%;min-width:80px;",
    "bar": "background:#4a90e2;height:10px;border-radius:6px;",
    "card": "display:inline-block;vertical-align:top;border:1px solid #d8dee4;border-radius:10px;"
            "padding:10px 12px;margin:0 10px 10px 0;min-width:180px;",
    "card-label": "font-size:11px;color:#5b6673;text-transform:uppercase;letter-spacing:.04em;",
    "card-value": "font-size:22px;font-weight:700;",
    "card-sub": "font-size:12px;color:#5b6673;",
}


def esc(value):
    return html.escape(str(value), quote=True)


def load_rows():
    text = SOURCE.read_text()
    match = re.search(r'<script id="bench-data" type="application/json">\n(.*?)\n</script>', text, re.S)
    if not match:
        raise SystemExit("bench-data block not found in docs/benchmarks.html")
    return json.loads(match.group(1))


def fmt(value, digits=1):
    if value is None or value == "":
        return "—"
    try:
        number = float(value)
    except (TypeError, ValueError):
        return esc(value)
    if digits == 0:
        return f"{number:,.0f}"
    return f"{number:,.{digits}f}"


def value(row, key):
    try:
        return float(row.get(key))
    except (TypeError, ValueError):
        return None


def best_row(rows, key, order):
    pairs = [(row, value(row, key)) for row in rows]
    pairs = [(row, val) for row, val in pairs if val is not None]
    if not pairs:
        return None
    return (min if order == "asc" else max)(pairs, key=lambda pair: pair[1])


def summary(rows):
    cards = []
    for area, _title, unit, order, key, _cols in AREAS:
        for platform in ("iOS", "Android"):
            subset = [r for r in rows if r["area"] == area and r["platform"] == platform]
            winner = best_row(subset, key, order)
            if not winner:
                continue
            row, val = winner
            cards.append(
                f'<div style="{STYLE["card"]}">'
                f'<div style="{STYLE["card-label"]}">{esc(platform)} · {esc(area)}</div>'
                f'<div style="{STYLE["card-value"]}">{fmt(val)} <span style="font-size:13px;font-weight:500;'
                f'color:#5b6673">{esc(unit)}</span></div>'
                f'<div style="{STYLE["card-sub"]}">{esc(row["device"])} · {esc(row["runtime"])}</div>'
                f"</div>"
            )
    return f'<div>{announce(rows)}</div>' + "".join(cards)


def announce(rows):
    areas = {area: sum(r["area"] == area for r in rows) for area, *_ in AREAS}
    counts = " · ".join(f"{name}: {count}" for name, count in areas.items())
    return (f'<div style="{STYLE["note"]}">Static snapshot of {len(rows)} measured rows '
            f'({counts}). Interactive version and raw tables: <a href="{REPO}">{esc(REPO.replace("https://", ""))}</a>.</div>')


def area_section(rows, area, title, unit, order, key, columns):
    subset = [r for r in rows if r["area"] == area]
    if not subset:
        return ""
    pairs = [(row, value(row, key)) for row in subset]
    pairs = [(row, val) for row, val in pairs if val is not None]
    pairs.sort(key=lambda pair: pair[1], reverse=(order == "desc"))
    peak = max((val for _, val in pairs), default=1) or 1
    best = pairs[0][0] if pairs else None

    head = "".join(f'<th style="{STYLE["th"]}">{esc(label)}</th>' for label, _ in columns)
    head += f'<th style="{STYLE["th"]}">Relative</th><th style="{STYLE["th"]}">Note</th>'

    body = []
    for row, val in pairs:
        cells = []
        for index, (label, field) in enumerate(columns):
            raw = row.get(field)
            if isinstance(raw, (int, float)):
                style = STYLE["td"] + STYLE["num"] + (STYLE["best"] if row is best and field == key else "")
                cells.append(f'<td style="{style}">{fmt(raw, 0 if field == "loadMs" else 1)}</td>')
            else:
                cells.append(f'<td style="{STYLE["td"]}">{esc(raw or "")}</td>')
        width = max(1.0, val / peak * 100)
        bar = (f'<div style="{STYLE["bar-track"]}"><div style="{STYLE["bar"]};width:{width:.1f}%"></div></div>')
        cells.append(f'<td style="{STYLE["td"]}">{bar}</td>')
        cells.append(f'<td style="{STYLE["td"] + STYLE["note-cell"]}">{esc(row.get("note", ""))}</td>')
        body.append("<tr>" + "".join(cells) + "</tr>")

    return (f'<h2 style="{STYLE["h2"]}">{esc(area)} — {esc(title)} ({esc(unit)}, '
            f'{"higher" if order == "desc" else "lower"} is better)</h2>'
            f'<table style="{STYLE["table"]}"><thead><tr>{head}</tr></thead>'
            f'<tbody>{"".join(body)}</tbody></table>')


def specs_section():
    head = "".join(f'<th style="{STYLE["th"]}">{label}</th>'
                   for label in ("Device", "Model code", "SoC", "RAM", "OS"))
    body = "".join(
        "<tr>" + "".join(f'<td style="{STYLE["td"]}">{esc(cell)}</td>' for cell in row) + "</tr>"
        for row in SPECS
    )
    return (f'<h2 style="{STYLE["h2"]}">Device specs — SoC, RAM, OS</h2>'
            f'<table style="{STYLE["table"]}"><thead><tr>{head}</tr></thead><tbody>{body}</tbody></table>'
            f'<p style="{STYLE["note"]}">RAM is nominal; kernels report less (11.4 GB usable on the '
            f'S25 Ultra, 7.3 GB on the TCL).</p>')


def build():
    rows = load_rows()
    sections = "".join(area_section(rows, *area) for area in AREAS)
    document = (
        f'<div style="{STYLE["wrap"]}">'
        f'<h2 style="{STYLE["h2"]}">MyLEO benchmarks — Confluence snapshot</h2>'
        f"{summary(rows)}"
        f"{sections}"
        f"{specs_section()}"
        f'<p style="{STYLE["note"]}">Generated from docs/benchmarks.html. Energy figures are '
        f'estimates (iOS gauge/Power Profiler; Android integrated current or charge-counter steps). '
        f'Compare lanes on the same device, not across devices. '
        f'<a href="{REPO}">Source repository</a>.</p>'
        "</div>"
    )
    TARGET.write_text(document)
    print(f"wrote {TARGET} ({len(rows)} rows, {TARGET.stat().st_size} bytes)")


if __name__ == "__main__":
    build()
