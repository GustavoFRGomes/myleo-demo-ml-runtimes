#!/usr/bin/env python3
"""Generate docs/confluence-benchmarks.html — a Confluence HTML-macro fragment.

The fragment keeps the full standalone UI by mounting it in a shadow root:

* the host <div> carries a static, inline-styled fallback (visible if scripts are stripped);
* a script clears the host, attaches a shadow root and copies the standalone page's
  markup, styles and app logic into it. Shadow DOM isolates the CSS from the
  Confluence page and the page's CSS from the widget.

It is a fragment (no doctype/html/head/body), so macros can insert it into an existing
document. Data comes from docs/benchmarks.html via docs/standalone-benchmarks.html.
"""
import html
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "benchmarks.html"
STANDALONE = ROOT / "standalone-benchmarks.html"
TARGET = ROOT / "confluence-benchmarks.html"

REPO = "https://github.com/GustavoFRGomes/myleo-demo-ml-runtimes"

# Devices intentionally left out of the Confluence copy.
EXCLUDED_DEVICES = {"Galaxy S26 Ultra"}

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


def static_summary(rows):
    cards = []
    for area, _title, unit, order, key, _cols in AREAS:
        for platform in ("iOS", "Android"):
            subset = [r for r in rows if r["area"] == area and r["platform"] == platform]
            pairs = [(row, value(row, key)) for row in subset]
            pairs = [(row, val) for row, val in pairs if val is not None]
            if not pairs:
                continue
            pick_best = min if order == "asc" else max
            pick_worst = max if order == "asc" else min
            best_row, best = pick_best(pairs, key=lambda pair: pair[1])
            worst_row, worst = pick_worst(pairs, key=lambda pair: pair[1])
            if best_row is worst_row:
                range_html = f"<strong>{fmt(best)}</strong>"
            else:
                range_html = f"{fmt(worst)} – <strong>{fmt(best)}</strong>"
            cards.append(
                f'<div style="{STYLE["card"]}">'
                f'<div style="{STYLE["card-label"]}">{esc(platform)} · {esc(area)}</div>'
                f'<div style="{STYLE["card-value"]};font-weight:400;font-size:20px;white-space:nowrap">{range_html} '
                f'<span style="font-size:13px;font-weight:500;color:#5b6673">{esc(unit)}</span></div>'
                f'<div style="{STYLE["card-sub"]}">worst: {esc(worst_row["device"])} · {esc(worst_row["runtime"])}</div>'
                f'<div style="{STYLE["card-sub"]}">best: {esc(best_row["device"])} · {esc(best_row["runtime"])}</div>'
                f"</div>"
            )
    return "".join(cards)


def static_area(rows, area, title, unit, order, key, columns):
    subset = [r for r in rows if r["area"] == area]
    if not subset:
        return ""
    pairs = [(row, value(row, key)) for row in subset]
    pairs = [(row, val) for row, val in pairs if val is not None]
    # Group by device (so repeated cells merge), best first inside each device.
    pairs.sort(key=lambda pair: (pair[0].get("device") or "",
                                 -pair[1] if order == "desc" else pair[1]))
    peak = max((val for _, val in pairs), default=1) or 1
    best = pairs[0][0] if pairs else None

    head = "".join(f'<th style="{STYLE["th"]}">{esc(label)}</th>' for label, _ in columns)
    head += f'<th style="{STYLE["th"]}">Relative</th><th style="{STYLE["th"]}">Note</th>'
    span_keys = {"device", "backend", "model"}
    grouped = [(row, val) for row, val in pairs]
    spans = {field: [1] * len(grouped) for field in span_keys}
    for field in span_keys:
        index = 0
        while index < len(grouped):
            nxt = index + 1
            while nxt < len(grouped) and (grouped[nxt][0].get(field) or "") == (grouped[index][0].get(field) or ""):
                nxt += 1
            spans[field][index] = nxt - index
            for skip in range(index + 1, nxt):
                spans[field][skip] = 0
            index = nxt

    body = []
    for position, (row, val) in enumerate(pairs):
        cells = []
        for _label, field in columns:
            raw = row.get(field)
            if field in span_keys:
                span = spans[field][position]
                if span == 0:
                    continue
                attr = f' rowspan="{span}"' if span > 1 else ""
                cells.append(f'<td{attr} style="{STYLE["td"]}">{esc(raw or "")}</td>')
            elif isinstance(raw, (int, float)):
                style = STYLE["td"] + STYLE["num"] + (STYLE["best"] if row is best and field == key else "")
                cells.append(f'<td style="{style}">{fmt(raw, 0 if field == "loadMs" else 1)}</td>')
            else:
                cells.append(f'<td style="{STYLE["td"]}">{esc(raw or "")}</td>')
        width = max(1.0, val / peak * 100)
        cells.append(f'<td style="{STYLE["td"]}"><div style="{STYLE["bar-track"]}">'
                     f'<div style="{STYLE["bar"]};width:{width:.1f}%"></div></div></td>')
        cells.append(f'<td style="{STYLE["td"] + STYLE["note-cell"]}">{esc(row.get("note", ""))}</td>')
        body.append("<tr>" + "".join(cells) + "</tr>")
    return (f'<h2 style="{STYLE["h2"]}">{esc(area)} — {esc(title)} ({esc(unit)}, '
            f'{"higher" if order == "desc" else "lower"} is better)</h2>'
            f'<table style="{STYLE["table"]}"><thead><tr>{head}</tr></thead>'
            f'<tbody>{"".join(body)}</tbody></table>')


def static_specs():
    head = "".join(f'<th style="{STYLE["th"]}">{label}</th>'
                   for label in ("Device", "Model code", "SoC", "RAM", "OS"))
    body = "".join("<tr>" + "".join(f'<td style="{STYLE["td"]}">{esc(cell)}</td>' for cell in row) + "</tr>"
                   for row in SPECS if row[0] not in EXCLUDED_DEVICES)
    return (f'<h2 style="{STYLE["h2"]}">Device specs — SoC, RAM, OS</h2>'
            f'<table style="{STYLE["table"]}"><thead><tr>{head}</tr></thead><tbody>{body}</tbody></table>'
            f'<p style="{STYLE["note"]}">RAM is nominal; kernels report less (11.4 GB usable on the '
            f'S25 Ultra, 7.3 GB on the TCL).</p>')


def static_fallback(rows):
    sections = "".join(static_area(rows, *area) for area in AREAS)
    return (f'<div style="{STYLE["wrap"]}">'
            f'<h2 style="{STYLE["h2"]}">MyLEO benchmarks — Confluence snapshot</h2>'
            f'<p style="{STYLE["note"]}">Static view — scripts are disabled or stripped. '
            f'{len(rows)} measured rows. Interactive version: '
            f'<a href="{REPO}">{esc(REPO.replace("https://", ""))}</a>.</p>'
            f"{static_summary(rows)}{sections}{static_specs()}"
            f'<p style="{STYLE["note"]}">Generated from docs/benchmarks.html. Energy figures are '
            f'estimates. Compare lanes on the same device, not across devices.</p></div>')


def strip_cards(markup):
    """Drop the standalone page's summary cards so they can be rebuilt from the filtered rows."""
    start = markup.find('<div class="cards">')
    if start < 0:
        return markup
    depth = 0
    index = start
    while index < len(markup):
        if markup.startswith("<div", index):
            depth += 1
        elif markup.startswith("</div>", index):
            depth -= 1
            if depth == 0:
                return markup[:start] + markup[index + 6:]
        index += 1
    return markup


def extract_standalone():
    text = STANDALONE.read_text()
    css = re.search(r"<style>(.*?)</style>", text, re.S)
    body = re.search(r"<body>(.*?)</body>", text, re.S)
    app = None
    for match in re.finditer(r"<script>(.*?)</script>", text, re.S):
        if "const DATA" in match.group(1):
            app = match.group(1)
            break
    if not (css and body and app):
        raise SystemExit("standalone page is missing its style, body or app script")
    markup = re.sub(r"<noscript>.*?</noscript>", "", body.group(1), flags=re.S)
    markup = re.sub(r"<script.*?</script>", "", markup, flags=re.S)
    return css.group(1), markup.strip(), app


def adapt_css(css):
    css = css.replace(":root {", ":host {")
    css = css.replace("\n  body {", "\n  :host {")
    return css


def adapt_app(app, rows):
    if 'const DATA = JSON.parse(document.getElementById("bench-data-LOCAL").textContent);' not in app:
        raise SystemExit("data line changed; update adapt_app")
    app = app.replace(
        'const DATA = JSON.parse(document.getElementById("bench-data-LOCAL").textContent);',
        f"const DATA = {json.dumps(rows)};",
    )
    # The app and its styles live in a shadow root: everything DOM-facing must be scoped.
    app = app.replace("document.getElementById(", "root.getElementById(")
    app = app.replace("document.querySelectorAll(", "root.querySelectorAll(")
    app = app.replace("document.addEventListener(", "root.addEventListener(")
    app = app.replace('document.body.dataset.ready = "1";', 'root.host.setAttribute("data-ready", "1");')
    # The hash belongs to the host page, not us.
    app = re.sub(r"\n\s*if \(location\.hash\.slice\(1\) !== state\.area\).*?;", "", app, flags=re.S)
    app = re.sub(r"\n\s*const initial = decodeURIComponent\(location\.hash\.slice\(1\)\);.*?\n  }}", "", app, flags=re.S)
    return app


def build():
    rows = [row for row in load_rows() if row.get("device") not in EXCLUDED_DEVICES]
    css, markup, app = extract_standalone()

    # The standalone markup carries cards and specs built from every row; rebuild
    # the cards for the filtered set and drop excluded specs rows.
    import standalone_benchmarks as standalone
    markup = strip_cards(markup)
    cards = standalone.summary_cards(rows)
    markup = markup.replace('<div class="tabs"', cards + '\n  <div class="tabs"', 1)
    for device in EXCLUDED_DEVICES:
        markup = re.sub(r"<tr><td>" + re.escape(device) + r"</td>.*?</tr>", "", markup, flags=re.S)
    # Keep the fragment free of excluded device names, including in notes.
    css = adapt_css(css)
    app = adapt_app(app, rows)
    fallback = static_fallback(rows)

    document = (
        '<div id="myleo-bench-host">' + fallback + "</div>\n"
        "<script>\n"
        "(function () {\n"
        '  var host = document.getElementById("myleo-bench-host");\n'
        "  if (!host || !host.attachShadow) return;\n"
        '  host.innerHTML = "";\n'
        '  var root = host.attachShadow({ mode: "open" });\n'
        f"  var css = {json.dumps(css)};\n"
        f"  var markup = {json.dumps(markup)};\n"
        '  try {\n'
        "    if (window.CSSStyleSheet) {\n"
        "      var sheet = new CSSStyleSheet();\n"
        "      sheet.replaceSync(css);\n"
        "      root.adoptedStyleSheets = [sheet];\n"
        "      root.innerHTML = markup;\n"
        "    } else {\n"
        '      root.innerHTML = "<style>" + css + "</style>" + markup;\n'
        "    }\n"
        "  } catch (e) {\n"
        '    root.innerHTML = "<style>" + css + "</style>" + markup;\n'
        "  }\n"
        f"  {app}\n"
        "})();\n"
        "</script>\n"
    )
    TARGET.write_text(document)
    print(f"wrote {TARGET} ({len(rows)} rows, {TARGET.stat().st_size} bytes)")
    if "</script>" in css or "</script>" in markup:
        print("warning: embedded markup contains a closing script tag", file=sys.stderr)


if __name__ == "__main__":
    build()
