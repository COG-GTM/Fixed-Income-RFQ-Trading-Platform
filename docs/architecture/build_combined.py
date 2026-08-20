"""Build the combined self-contained HTML from the four architecture docs.

Strips the collapsed Mermaid <details> blocks and inlines the rendered PNGs so the
HTML can be opened or printed without the repository present.
"""
import base64
import pathlib
import re

import markdown

HERE = pathlib.Path(__file__).parent
DOCS = [
    ("Current State", "current-state.md"),
    ("Target State — AWS", "target-state.md"),
    ("Open Questions", "open-questions.md"),
    ("Migration Plan", "migration-plan.md"),
]

DETAILS = re.compile(r"<details>.*?</details>", re.DOTALL)
IMG = re.compile(r"!\[(?P<alt>[^\]]*)\]\((?P<src>diagrams/[^)]+)\)")


def inline_image(match):
    src = HERE / match.group("src")
    data = base64.b64encode(src.read_bytes()).decode()
    return f'<img alt="{match.group("alt")}" src="data:image/png;base64,{data}" />'


body = []
for title, name in DOCS:
    text = (HERE / name).read_text()
    text = DETAILS.sub("", text)
    text = IMG.sub(inline_image, text)
    text = re.sub(r"\]\((?:[a-z-]+)\.md(#[^)]*)?\)", "]()", text)
    html = markdown.markdown(text, extensions=["tables", "md_in_html"])
    body.append(f'<section id="{name[:-3]}">{html}</section>')

STYLE = """
@page { size: A3 landscape; margin: 12mm; }
body { font-family: "DejaVu Sans", Helvetica, Arial, sans-serif; font-size: 10.5pt;
       line-height: 1.45; color: #111; }
section { page-break-before: always; }
section:first-of-type { page-break-before: avoid; }
h1 { font-size: 20pt; border-bottom: 2px solid #111; padding-bottom: 4px; }
h2 { font-size: 14pt; margin-top: 20px; }
h3 { font-size: 12pt; }
table { border-collapse: collapse; width: 100%; margin: 10px 0; table-layout: auto;
        page-break-inside: auto; }
th, td { border: 1px solid #999; padding: 4px 6px; text-align: left;
         vertical-align: top; font-size: 9pt; word-wrap: break-word; }
th { background: #f0f0f0; }
tr { page-break-inside: avoid; }
code { font-family: "DejaVu Sans Mono", monospace; font-size: 8.8pt; background: #f5f5f5;
       padding: 0 2px; }
img { max-width: 100%; height: auto; page-break-inside: avoid; }
"""

out = f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<title>Fixed-Income RFQ Trading Platform — Legacy-to-AWS Migration Architecture Package</title>
<style>{STYLE}</style></head><body>
<h1>Fixed-Income RFQ Trading Platform — Legacy-to-AWS Migration Architecture Package</h1>
<p>Repository <code>COG-GTM/Fixed-Income-RFQ-Trading-Platform</code>, commit <code>0b72d81</code>.
Contents: current state, AWS target state (including the assumed service list, the assumed
guardrails in force and the guardrail-compliance table), open questions, and the migration plan
ending at production cutover.</p>
{''.join(body)}
</body></html>
"""
(HERE / "migration-architecture-package.html").write_text(out)
print("wrote migration-architecture-package.html")
