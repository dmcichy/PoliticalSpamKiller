"""
Step 3: Analyze labeled data quality before training.

Reads data/labeled.csv and prints:
  - Spam vs. ham counts and percentages
  - Confidence distribution histogram (text-based)
  - Duplicate body detection
  - Low-confidence samples for manual spot-check
  - Random POLITICAL_SPAM samples for sanity-check

Usage:
    python step3_analyze.py
"""

import sys
import io
from collections import Counter

import pandas as pd

# Force UTF-8 stdout on Windows so emoji / special chars don't crash prints
if sys.stdout.encoding != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from config import LABELED_CSV, LABEL_SPAM, LABEL_NOT


def text_histogram(values: list[float], bins: int = 10, width: int = 40) -> None:
    """Print a simple ASCII histogram."""
    counts, edges = pd.cut(values, bins=bins, retbins=True)
    freq = counts.value_counts().sort_index()
    max_count = freq.max() if freq.max() > 0 else 1
    for interval, count in freq.items():
        bar = "#" * int(count / max_count * width)
        print(f"  {interval.left:.1f}-{interval.right:.1f}  {count:>6,}  {bar}")


def main() -> None:
    if not LABELED_CSV.exists():
        print(f"ERROR: {LABELED_CSV} not found. Run step2_label.py first.")
        sys.exit(1)

    df = pd.read_csv(LABELED_CSV)
    total = len(df)
    print(f"Labeled dataset: {total:,} messages\n")

    # ── Class distribution ────────────────────────────────────────────
    label_counts = df["label"].value_counts()
    print("=== Class Distribution ===")
    for label, count in label_counts.items():
        pct = count / total * 100
        print(f"  {label:20s}  {count:>7,}  ({pct:.1f}%)")
    print()

    spam_df = df[df["label"] == LABEL_SPAM]
    not_df = df[df["label"] == LABEL_NOT]

    # ── Confidence distribution ───────────────────────────────────────
    print("=== Confidence Distribution (all) ===")
    text_histogram(df["confidence"].tolist())
    print()

    if len(spam_df) > 0:
        print(f"=== Confidence Distribution ({LABEL_SPAM} only) ===")
        text_histogram(spam_df["confidence"].tolist())
        print()

    # ── Duplicates ────────────────────────────────────────────────────
    body_counts = df["body"].value_counts()
    dups = body_counts[body_counts > 1]
    print(f"=== Duplicates ===")
    print(f"  Unique bodies: {body_counts.shape[0]:,}")
    print(f"  Duplicate bodies (>1 occurrence): {len(dups):,}")
    if len(dups) > 0:
        print(f"  Top 5 most repeated:")
        for body, count in dups.head(5).items():
            label = df[df["body"] == body]["label"].iloc[0]
            print(f"    [{label}] x{count}: {str(body)[:80]}...")
    print()

    # ── Low-confidence samples (borderline) ───────────────────────────
    low_conf = df[(df["confidence"] >= 0.3) & (df["confidence"] <= 0.7)]
    print(f"=== Borderline Samples (confidence 0.3-0.7): {len(low_conf):,} ===")
    sample = low_conf.sample(n=min(20, len(low_conf)), random_state=42) if len(low_conf) > 0 else low_conf
    for _, row in sample.iterrows():
        print(f"  [{row['label']}  conf={row['confidence']:.2f}]  "
              f"From {row['address']}: {str(row['body'])[:100]}")
        print(f"    Reason: {row['reason']}")
    print()

    # ── Random POLITICAL_SPAM samples ─────────────────────────────────
    print(f"=== Random {LABEL_SPAM} Samples (up to 20) ===")
    spam_sample = spam_df.sample(n=min(20, len(spam_df)), random_state=42) if len(spam_df) > 0 else spam_df
    for _, row in spam_sample.iterrows():
        print(f"  [conf={row['confidence']:.2f}]  "
              f"From {row['address']}: {str(row['body'])[:120]}")
        print(f"    Reason: {row['reason']}")
    print()

    # ── Random NOT samples (spot-check for false negatives) ───────────
    print(f"=== Random {LABEL_NOT} Samples (up to 15) ===")
    not_sample = not_df.sample(n=min(15, len(not_df)), random_state=42) if len(not_df) > 0 else not_df
    for _, row in not_sample.iterrows():
        print(f"  [conf={row['confidence']:.2f}]  "
              f"From {row['address']}: {str(row['body'])[:120]}")
    print()

    # ── Summary stats ─────────────────────────────────────────────────
    print("=== Summary ===")
    print(f"  Total labeled:     {total:,}")
    print(f"  Political spam:    {len(spam_df):,} ({len(spam_df)/total*100:.1f}%)")
    print(f"  Not spam:          {len(not_df):,} ({len(not_df)/total*100:.1f}%)")
    print(f"  Mean confidence:   {df['confidence'].mean():.3f}")
    print(f"  Median confidence: {df['confidence'].median():.3f}")
    print(f"  Borderline (0.3-0.7): {len(low_conf):,}")
    high_conf_spam = spam_df[spam_df["confidence"] >= 0.9]
    print(f"  High-conf spam (>=0.9): {len(high_conf_spam):,}")
    print()

    if len(low_conf) > 20:
        print("*** ATTENTION: Many borderline samples. Review them before training! ***")
    if len(spam_df) < 50:
        print("*** WARNING: Very few spam samples. Model may underperform. ***")
        print("    Consider labeling more messages or augmenting the dataset.")


if __name__ == "__main__":
    main()
