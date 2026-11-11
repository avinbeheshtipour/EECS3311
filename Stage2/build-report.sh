#!/usr/bin/env bash
# Builds stage2-report.pdf (repository root) from Stage2/Avin-Stage2-EECS3311.md.
# Needs Node.js (for npx) and Google Chrome. The Mermaid UML diagram is rendered in the PDF.
# Usage: bash Stage2/build-report.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/Stage2/Avin-Stage2-EECS3311.md"
OUT="$ROOT/stage2-report.pdf"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

npx -y marked@12.0.2 --gfm -i "$SRC" -o "$WORK/body.html"

{
cat <<'HTML'
<!doctype html>
<html><head><meta charset="utf-8"><title>stage2-report</title>
<style>
  @page { size: Letter; margin: 16mm 14mm; }
  body { font: 10.5pt/1.45 -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; color: #1a1a1a; }
  h1 { font-size: 20pt; border-bottom: 2px solid #333; padding-bottom: 4px; }
  h2 { font-size: 15pt; border-bottom: 1px solid #bbb; padding-bottom: 2px; margin-top: 26px; break-after: avoid; }
  h3 { font-size: 12pt; margin-top: 18px; break-after: avoid; }
  table { border-collapse: collapse; width: 100%; margin: 8px 0; font-size: 9pt; }
  th, td { border: 1px solid #bbb; padding: 4px 6px; vertical-align: top; text-align: left; }
  th { background: #eee; }
  tr { break-inside: avoid; }
  code { font: 8.8pt Menlo, Consolas, monospace; background: #f3f3f3; padding: 0 2px; border-radius: 2px; }
  pre { background: #f6f6f6; padding: 8px; white-space: pre-wrap; }
  pre code { background: none; }
  blockquote { border-left: 3px solid #999; margin: 8px 0; padding: 2px 10px; color: #444; background: #fafafa; }
  a { color: #0b57d0; word-break: break-all; }
  hr { border: 0; border-top: 1px solid #ddd; }
  .mermaid { text-align: center; break-inside: avoid; }
  .mermaid svg { max-width: 100% !important; height: auto; }
</style></head><body>
HTML
cat "$WORK/body.html"
cat <<'HTML'
<script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js"></script>
<script>
  document.querySelectorAll('pre > code.language-mermaid').forEach(code => {
    const div = document.createElement('div');
    div.className = 'mermaid';
    div.textContent = code.textContent;
    code.parentElement.replaceWith(div);
  });
  mermaid.initialize({ startOnLoad: false, theme: 'neutral', classDiagram: { useMaxWidth: true } });
  mermaid.run();
</script>
</body></html>
HTML
} > "$WORK/report.html"

"$CHROME" --headless=new --disable-gpu --no-pdf-header-footer --virtual-time-budget=15000 \
  --print-to-pdf="$OUT" "file://$WORK/report.html" 2>/dev/null

echo "Wrote $OUT"
