"""
Step 4: Generate an interactive HTML review page for labeled data.

Opens a self-contained HTML file in the default browser where you can:
  - Browse ALL messages labeled POLITICAL_SPAM (sorted by confidence, lowest first)
  - Browse borderline NOT messages (confidence < 0.8) in case of false negatives
  - Flip any label with one click
  - Filter by confidence range, search by text
  - Export a corrections CSV that step5 (apply_corrections) will merge back

Usage:
    python step4_review.py            # generates and opens review.html
    python step4_review.py --no-open  # generate only, don't auto-open
"""

import argparse
import csv
import html
import io
import json
import sys
import webbrowser
from pathlib import Path

if sys.stdout.encoding != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from config import LABELED_CSV, DATA_DIR, LABEL_SPAM, LABEL_NOT

REVIEW_HTML = DATA_DIR / "review.html"


def load_review_rows() -> list[dict]:
    """Load rows that need human review."""
    rows = []
    with open(LABELED_CSV, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            conf = float(row.get("confidence", 0))
            label = row.get("label", "")
            # Include: all POLITICAL_SPAM + any NOT with confidence < 0.8
            if label == LABEL_SPAM or (label == LABEL_NOT and conf < 0.80):
                rows.append({
                    "id": int(row["id"]),
                    "address": row.get("address", ""),
                    "body": row.get("body", ""),
                    "label": label,
                    "confidence": conf,
                    "reason": row.get("reason", ""),
                })
    rows.sort(key=lambda r: (r["label"] != LABEL_SPAM, r["confidence"]))
    return rows


def generate_html(rows: list[dict]) -> str:
    spam_count = sum(1 for r in rows if r["label"] == LABEL_SPAM)
    not_count = len(rows) - spam_count

    rows_json = json.dumps(rows, ensure_ascii=False)

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>PTK Label Review</title>
<style>
  :root {{
    --bg: #0f1117;
    --surface: #1a1d27;
    --border: #2a2d3a;
    --text: #e1e4ed;
    --muted: #8b8fa3;
    --spam: #ef4444;
    --spam-bg: #1c1012;
    --not: #22c55e;
    --not-bg: #0f1a13;
    --accent: #6366f1;
    --accent-hover: #818cf8;
    --warn: #f59e0b;
  }}
  * {{ box-sizing: border-box; margin: 0; padding: 0; }}
  body {{
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    background: var(--bg);
    color: var(--text);
    line-height: 1.5;
    padding: 20px;
  }}
  h1 {{ font-size: 1.5rem; margin-bottom: 4px; }}
  .subtitle {{ color: var(--muted); margin-bottom: 20px; font-size: 0.9rem; }}
  .toolbar {{
    display: flex;
    gap: 12px;
    align-items: center;
    flex-wrap: wrap;
    margin-bottom: 16px;
    padding: 12px;
    background: var(--surface);
    border-radius: 8px;
    border: 1px solid var(--border);
  }}
  .toolbar label {{ color: var(--muted); font-size: 0.85rem; }}
  .toolbar input, .toolbar select {{
    background: var(--bg);
    border: 1px solid var(--border);
    color: var(--text);
    padding: 6px 10px;
    border-radius: 4px;
    font-size: 0.85rem;
  }}
  .toolbar input:focus, .toolbar select:focus {{
    outline: none;
    border-color: var(--accent);
  }}
  #searchBox {{ width: 250px; }}
  .stats {{
    display: flex;
    gap: 16px;
    margin-bottom: 16px;
    font-size: 0.85rem;
  }}
  .stat {{ padding: 8px 14px; border-radius: 6px; background: var(--surface); border: 1px solid var(--border); }}
  .stat b {{ color: var(--accent); }}
  .bulk-bar {{
    display: flex;
    gap: 10px;
    align-items: center;
    margin-bottom: 12px;
    padding: 10px 14px;
    background: #121520;
    border: 1px solid var(--border);
    border-radius: 8px;
  }}
  .bulk-bar .bulk-label {{ color: var(--muted); font-size: 0.85rem; margin-right: 4px; }}
  .btn-bulk-not {{ background: transparent; border: 1px solid var(--not); color: var(--not); }}
  .btn-bulk-not:hover {{ background: var(--not); color: #000; }}
  .btn-bulk-spam {{ background: transparent; border: 1px solid var(--spam); color: var(--spam); }}
  .btn-bulk-spam:hover {{ background: var(--spam); color: #fff; }}
  .corrections-bar {{
    display: flex;
    gap: 12px;
    align-items: center;
    margin-bottom: 16px;
    padding: 10px 14px;
    background: #1a1610;
    border: 1px solid #4a3f20;
    border-radius: 8px;
  }}
  .corrections-bar.hidden {{ display: none; }}
  .corrections-bar span {{ color: var(--warn); font-weight: 600; }}
  .btn {{
    padding: 6px 14px;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.85rem;
    font-weight: 500;
  }}
  .btn-primary {{ background: var(--accent); color: #fff; }}
  .btn-primary:hover {{ background: var(--accent-hover); }}
  .btn-outline {{ background: transparent; border: 1px solid var(--border); color: var(--text); }}
  .btn-outline:hover {{ border-color: var(--accent); }}
  .card {{
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 14px 16px;
    margin-bottom: 8px;
    transition: border-color 0.15s;
  }}
  .card:hover {{ border-color: #3a3d4a; }}
  .card.is-spam {{ border-left: 3px solid var(--spam); background: var(--spam-bg); }}
  .card.is-not {{ border-left: 3px solid var(--not); background: var(--not-bg); }}
  .card.is-flipped {{ border-left-style: dashed; opacity: 0.85; }}
  .card-header {{
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;
  }}
  .label-badge {{
    display: inline-block;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 0.75rem;
    font-weight: 700;
    text-transform: uppercase;
  }}
  .label-badge.spam {{ background: var(--spam); color: #fff; }}
  .label-badge.not {{ background: var(--not); color: #000; }}
  .conf {{ color: var(--muted); font-size: 0.8rem; }}
  .sender {{ color: var(--muted); font-size: 0.8rem; margin-bottom: 6px; }}
  .body-text {{ white-space: pre-wrap; word-break: break-word; font-size: 0.9rem; max-height: 200px; overflow-y: auto; }}
  .reason {{ color: var(--muted); font-size: 0.8rem; margin-top: 6px; font-style: italic; }}
  .flip-btn {{
    padding: 4px 10px;
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.75rem;
    font-weight: 600;
    border: 1px solid;
    background: transparent;
    transition: all 0.15s;
  }}
  .flip-btn.to-not {{ border-color: var(--not); color: var(--not); }}
  .flip-btn.to-not:hover {{ background: var(--not); color: #000; }}
  .flip-btn.to-spam {{ border-color: var(--spam); color: var(--spam); }}
  .flip-btn.to-spam:hover {{ background: var(--spam); color: #fff; }}
  .flip-btn.undo {{ border-color: var(--warn); color: var(--warn); }}
  .flip-btn.undo:hover {{ background: var(--warn); color: #000; }}
  .pagination {{
    display: flex;
    gap: 8px;
    justify-content: center;
    align-items: center;
    margin: 20px 0;
  }}
  .pagination button {{ min-width: 36px; }}
  .empty-state {{ text-align: center; color: var(--muted); padding: 40px; }}
  #toast {{
    position: fixed;
    bottom: 20px;
    right: 20px;
    background: var(--not);
    color: #000;
    padding: 10px 20px;
    border-radius: 6px;
    font-weight: 600;
    display: none;
    z-index: 1000;
  }}
</style>
</head>
<body>

<h1>PTK Label Review</h1>
<p class="subtitle">
  Browse messages labeled by gpt-4o-mini. Flip any incorrect labels, then export corrections.
  <br>Showing: all POLITICAL_SPAM + low-confidence NOT messages (conf &lt; 0.8).
</p>

<div class="stats">
  <div class="stat">Total reviewable: <b id="totalCount">0</b></div>
  <div class="stat">Spam: <b id="spamCount" style="color:var(--spam)">0</b></div>
  <div class="stat">Not: <b id="notCount" style="color:var(--not)">0</b></div>
  <div class="stat">Visible: <b id="visibleCount">0</b></div>
</div>

<div class="toolbar">
  <label>Show:
    <select id="filterLabel">
      <option value="all">All</option>
      <option value="POLITICAL_SPAM">POLITICAL_SPAM only</option>
      <option value="NOT">NOT only</option>
      <option value="flipped">Flipped only</option>
    </select>
  </label>
  <label>Confidence:
    <input type="range" id="confMin" min="0" max="100" value="0" style="width:80px">
    <span id="confMinVal">0.00</span>
    &ndash;
    <input type="range" id="confMax" min="0" max="100" value="100" style="width:80px">
    <span id="confMaxVal">1.00</span>
  </label>
  <label>Search: <input type="text" id="searchBox" placeholder="Filter by message text..."></label>
  <label>Sort:
    <select id="sortOrder">
      <option value="conf-asc">Confidence (low first)</option>
      <option value="conf-desc">Confidence (high first)</option>
      <option value="id-asc">Message ID (oldest first)</option>
    </select>
  </label>
</div>

<div class="bulk-bar">
  <span class="bulk-label">Bulk actions on <b id="bulkCount">0</b> visible:</span>
  <button class="btn btn-bulk-not" onclick="bulkFlip('{LABEL_NOT}')">Mark All Visible NOT</button>
  <button class="btn btn-bulk-spam" onclick="bulkFlip('{LABEL_SPAM}')">Mark All Visible SPAM</button>
</div>

<div id="correctionsBar" class="corrections-bar hidden">
  <span id="correctionCount">0</span> corrections pending
  <button class="btn btn-primary" onclick="exportCorrections()">Export Corrections CSV</button>
  <button class="btn btn-outline" onclick="clearAllCorrections()">Clear All</button>
</div>

<div id="cardContainer"></div>

<div class="pagination">
  <button class="btn btn-outline" id="prevBtn" onclick="changePage(-1)">&laquo; Prev</button>
  <span id="pageInfo">1 / 1</span>
  <button class="btn btn-outline" id="nextBtn" onclick="changePage(1)">Next &raquo;</button>
  <label style="margin-left:12px; color:var(--muted); font-size:0.85rem">Per page:
    <select id="perPage" onchange="resetPage()">
      <option value="25">25</option>
      <option value="50" selected>50</option>
      <option value="100">100</option>
      <option value="250">250</option>
    </select>
  </label>
</div>

<div id="toast"></div>

<script>
const ALL_ROWS = {rows_json};
const corrections = {{}};  // id -> new label
let filtered = [];
let currentPage = 0;

function getPerPage() {{ return parseInt(document.getElementById('perPage').value); }}

function applyFilters() {{
  const labelFilter = document.getElementById('filterLabel').value;
  const confMin = parseInt(document.getElementById('confMin').value) / 100;
  const confMax = parseInt(document.getElementById('confMax').value) / 100;
  const search = document.getElementById('searchBox').value.toLowerCase();
  const sort = document.getElementById('sortOrder').value;

  filtered = ALL_ROWS.filter(r => {{
    const currentLabel = corrections[r.id] || r.label;
    if (labelFilter === 'flipped') return corrections[r.id] !== undefined;
    if (labelFilter !== 'all' && currentLabel !== labelFilter) return false;
    if (r.confidence < confMin || r.confidence > confMax) return false;
    if (search && !r.body.toLowerCase().includes(search) && !r.address.includes(search)) return false;
    return true;
  }});

  if (sort === 'conf-asc') filtered.sort((a, b) => a.confidence - b.confidence);
  else if (sort === 'conf-desc') filtered.sort((a, b) => b.confidence - a.confidence);
  else filtered.sort((a, b) => a.id - b.id);

  currentPage = 0;
  render();
}}

function render() {{
  const pp = getPerPage();
  const totalPages = Math.max(1, Math.ceil(filtered.length / pp));
  if (currentPage >= totalPages) currentPage = totalPages - 1;
  const start = currentPage * pp;
  const pageRows = filtered.slice(start, start + pp);

  const spamTotal = ALL_ROWS.filter(r => (corrections[r.id] || r.label) === '{LABEL_SPAM}').length;
  const notTotal = ALL_ROWS.filter(r => (corrections[r.id] || r.label) === '{LABEL_NOT}').length;

  document.getElementById('totalCount').textContent = ALL_ROWS.length.toLocaleString();
  document.getElementById('spamCount').textContent = spamTotal.toLocaleString();
  document.getElementById('notCount').textContent = notTotal.toLocaleString();
  document.getElementById('visibleCount').textContent = filtered.length.toLocaleString();
  document.getElementById('bulkCount').textContent = filtered.length.toLocaleString();
  document.getElementById('confMinVal').textContent = (parseInt(document.getElementById('confMin').value) / 100).toFixed(2);
  document.getElementById('confMaxVal').textContent = (parseInt(document.getElementById('confMax').value) / 100).toFixed(2);

  const corrCount = Object.keys(corrections).length;
  const bar = document.getElementById('correctionsBar');
  if (corrCount > 0) {{
    bar.classList.remove('hidden');
    document.getElementById('correctionCount').textContent = corrCount;
  }} else {{
    bar.classList.add('hidden');
  }}

  document.getElementById('pageInfo').textContent = `${{currentPage + 1}} / ${{totalPages}}`;
  document.getElementById('prevBtn').disabled = currentPage === 0;
  document.getElementById('nextBtn').disabled = currentPage >= totalPages - 1;

  const container = document.getElementById('cardContainer');
  if (pageRows.length === 0) {{
    container.innerHTML = '<div class="empty-state">No messages match the current filters.</div>';
    return;
  }}

  container.innerHTML = pageRows.map(r => {{
    const currentLabel = corrections[r.id] || r.label;
    const isFlipped = corrections[r.id] !== undefined;
    const isSpam = currentLabel === '{LABEL_SPAM}';
    const cardClass = `card ${{isSpam ? 'is-spam' : 'is-not'}} ${{isFlipped ? 'is-flipped' : ''}}`;

    let flipBtn;
    if (isFlipped) {{
      flipBtn = `<button class="flip-btn undo" onclick="undoFlip(${{r.id}})">Undo</button>`;
    }} else if (isSpam) {{
      flipBtn = `<button class="flip-btn to-not" onclick="flipLabel(${{r.id}}, '${{LABEL_NOT}}')">Mark NOT</button>`;
    }} else {{
      flipBtn = `<button class="flip-btn to-spam" onclick="flipLabel(${{r.id}}, '${{LABEL_SPAM}}')">Mark SPAM</button>`;
    }}

    const bodyEsc = escHtml(r.body || '');
    const reasonEsc = escHtml(r.reason || '');
    const addrEsc = escHtml(r.address || '');

    return `<div class="${{cardClass}}" id="card-${{r.id}}">
      <div class="card-header">
        <div>
          <span class="label-badge ${{isSpam ? 'spam' : 'not'}}">${{currentLabel}}</span>
          ${{isFlipped ? '<span style="color:var(--warn);font-size:0.75rem;margin-left:6px">CHANGED</span>' : ''}}
          <span class="conf">conf: ${{r.confidence.toFixed(2)}}</span>
        </div>
        <div>${{flipBtn}}</div>
      </div>
      <div class="sender">From: ${{addrEsc}} | ID: ${{r.id}}</div>
      <div class="body-text">${{bodyEsc}}</div>
      ${{reasonEsc ? `<div class="reason">LLM reason: ${{reasonEsc}}</div>` : ''}}
    </div>`;
  }}).join('');
}}

const LABEL_NOT = '{LABEL_NOT}';
const LABEL_SPAM = '{LABEL_SPAM}';

function escHtml(s) {{
  const d = document.createElement('div');
  d.textContent = s;
  return d.innerHTML;
}}

function flipLabel(id, newLabel) {{
  const orig = ALL_ROWS.find(r => r.id === id);
  if (orig && orig.label === newLabel) {{
    delete corrections[id];
  }} else {{
    corrections[id] = newLabel;
  }}
  render();
}}

function undoFlip(id) {{
  delete corrections[id];
  render();
}}

function bulkFlip(newLabel) {{
  const count = filtered.length;
  const labelName = newLabel === '{LABEL_NOT}' ? 'NOT' : 'POLITICAL_SPAM';
  if (!confirm(`Mark all ${{count}} visible messages as ${{labelName}}?`)) return;
  let flipped = 0;
  for (const r of filtered) {{
    const currentLabel = corrections[r.id] || r.label;
    if (currentLabel !== newLabel) {{
      if (r.label === newLabel) {{
        delete corrections[r.id];
      }} else {{
        corrections[r.id] = newLabel;
      }}
      flipped++;
    }}
  }}
  render();
  showToast(`Bulk-flipped ${{flipped}} messages to ${{labelName}}`);
}}

function clearAllCorrections() {{
  if (!confirm('Clear all ' + Object.keys(corrections).length + ' corrections?')) return;
  for (const k of Object.keys(corrections)) delete corrections[k];
  render();
}}

function changePage(delta) {{
  currentPage += delta;
  render();
  window.scrollTo(0, 0);
}}

function resetPage() {{
  currentPage = 0;
  render();
}}

function exportCorrections() {{
  const entries = Object.entries(corrections);
  if (entries.length === 0) {{ showToast('No corrections to export.'); return; }}

  let csvContent = 'id,original_label,corrected_label\\n';
  for (const [id, newLabel] of entries) {{
    const orig = ALL_ROWS.find(r => r.id === parseInt(id));
    csvContent += `${{id}},${{orig ? orig.label : '?'}},${{newLabel}}\\n`;
  }}

  const blob = new Blob([csvContent], {{ type: 'text/csv' }});
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'corrections.csv';
  a.click();
  URL.revokeObjectURL(url);
  showToast(`Exported ${{entries.length}} corrections to corrections.csv`);
}}

function showToast(msg) {{
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.style.display = 'block';
  setTimeout(() => {{ t.style.display = 'none'; }}, 3000);
}}

// Wire up filter controls
for (const id of ['filterLabel', 'confMin', 'confMax', 'sortOrder']) {{
  document.getElementById(id).addEventListener('change', applyFilters);
  document.getElementById(id).addEventListener('input', applyFilters);
}}
let searchTimeout;
document.getElementById('searchBox').addEventListener('input', () => {{
  clearTimeout(searchTimeout);
  searchTimeout = setTimeout(applyFilters, 300);
}});

applyFilters();
</script>
</body>
</html>"""


def main() -> None:
    if not LABELED_CSV.exists():
        print(f"ERROR: {LABELED_CSV} not found. Run step2_label.py first.")
        sys.exit(1)

    print("Loading labeled data ...", flush=True)
    rows = load_review_rows()
    spam_count = sum(1 for r in rows if r["label"] == LABEL_SPAM)
    print(f"  {len(rows):,} reviewable rows ({spam_count:,} POLITICAL_SPAM, "
          f"{len(rows) - spam_count:,} low-confidence NOT)")

    print("Generating review HTML ...", flush=True)
    html_content = generate_html(rows)
    REVIEW_HTML.write_text(html_content, encoding="utf-8")
    print(f"  Written to: {REVIEW_HTML}")

    parser = argparse.ArgumentParser()
    parser.add_argument("--no-open", action="store_true")
    args = parser.parse_args()

    if not args.no_open:
        print("Opening in browser ...")
        webbrowser.open(str(REVIEW_HTML))


if __name__ == "__main__":
    main()
