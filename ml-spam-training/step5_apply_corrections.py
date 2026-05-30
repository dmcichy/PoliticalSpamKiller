"""
Step 5: Apply corrections from the review tool back into labeled.csv.

Reads data/corrections.csv (exported from the review HTML) and updates
the corresponding rows in data/labeled.csv. Creates a backup first.

Usage:
    python step5_apply_corrections.py                         # looks for data/corrections.csv
    python step5_apply_corrections.py path/to/corrections.csv # explicit path
"""

import csv
import io
import shutil
import sys
from pathlib import Path

if sys.stdout.encoding != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from config import LABELED_CSV, DATA_DIR


def main() -> None:
    corrections_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DATA_DIR / "corrections.csv"

    if not corrections_path.exists():
        print(f"ERROR: {corrections_path} not found.")
        print("Export corrections from the review tool first (step4_review.py).")
        sys.exit(1)

    if not LABELED_CSV.exists():
        print(f"ERROR: {LABELED_CSV} not found.")
        sys.exit(1)

    corr = {}
    with open(corrections_path, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            corr[int(row["id"])] = row["corrected_label"]

    if not corr:
        print("No corrections found in file.")
        sys.exit(0)

    print(f"Loaded {len(corr)} corrections from {corrections_path}")

    backup = LABELED_CSV.with_suffix(".csv.bak")
    shutil.copy2(LABELED_CSV, backup)
    print(f"Backed up labeled.csv to {backup.name}")

    rows = []
    changed = 0
    with open(LABELED_CSV, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        fieldnames = reader.fieldnames
        for row in reader:
            rid = int(row["id"])
            if rid in corr and row["label"] != corr[rid]:
                row["label"] = corr[rid]
                row["reason"] = f"[HUMAN CORRECTED] {row.get('reason', '')}"
                changed += 1
            rows.append(row)

    with open(LABELED_CSV, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    print(f"Applied {changed} corrections to {LABELED_CSV.name}")
    if changed < len(corr):
        print(f"  ({len(corr) - changed} corrections matched existing labels, no change needed)")


if __name__ == "__main__":
    main()
