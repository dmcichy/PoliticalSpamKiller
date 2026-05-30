"""
Step 2: Auto-label messages via OpenAI gpt-4o-mini.

Reads data/messages.csv, batches messages, sends each batch to gpt-4o-mini
with a strict classification prompt, and writes results to data/labeled.csv.

Resumable: if labeled.csv already exists, rows already labeled are skipped.
Use --sample N to label only a random sample first for quality-checking.

Usage:
    doppler run --project mycredentials --config prd_personal -- python step2_label.py
    doppler run ... -- python step2_label.py --sample 500
"""

import argparse
import csv
import io
import json
import random
import sys
import time
from pathlib import Path

if sys.stdout.encoding != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

import openai
import pandas as pd

from config import (
    BATCH_SIZE,
    DATA_DIR,
    LABEL_NOT,
    LABEL_SPAM,
    LABELED_CSV,
    MAX_RETRIES,
    MESSAGES_CSV,
    OPENAI_MODEL,
    RETRY_DELAY_SEC,
    get_openai_key,
)

SYSTEM_PROMPT = f"""\
You are a binary SMS classifier. Your ONLY job is to decide whether each SMS
message is **US political campaign / fundraising / advocacy spam** or not.

POLITICAL_SPAM includes: campaign donation asks, political fundraising,
partisan advocacy texts, political surveys/polls, candidate promotion,
PAC messages, ballot-measure advocacy, voter-mobilization spam, and any
unsolicited political text from an organization or campaign.

NOT includes: everything else — personal texts, merchant/business messages,
delivery notifications, 2FA codes, appointment reminders, bank alerts,
legitimate non-political messages, even if they mention a political topic
in passing (e.g., a friend texting about the news).

For each message, respond with a JSON object:
  {{"id": <int>, "label": "{LABEL_SPAM}" | "{LABEL_NOT}",
   "confidence": <float 0-1>, "reason": "<1-sentence explanation>"}}

Respond ONLY with a JSON array of these objects, one per message. No
markdown, no extra text."""


def build_batch_prompt(batch: list[dict]) -> str:
    """Format a batch of messages for the user prompt."""
    lines = []
    for row in batch:
        body_trunc = row["body"][:500]
        lines.append(f'[{row["id"]}] From: {row["address"]} | "{body_trunc}"')
    return "\n".join(lines)


def label_batch(
    client: openai.OpenAI, batch: list[dict]
) -> list[dict]:
    """Call gpt-4o-mini for one batch; returns list of label dicts."""
    user_msg = build_batch_prompt(batch)

    for attempt in range(1, MAX_RETRIES + 1):
        try:
            resp = client.chat.completions.create(
                model=OPENAI_MODEL,
                messages=[
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": user_msg},
                ],
                temperature=0.0,
                response_format={"type": "json_object"},
            )
            raw = resp.choices[0].message.content.strip()
            parsed = json.loads(raw)

            # Normalise: the model may return a dict wrapper or a bare list
            if isinstance(parsed, dict):
                # Find the first list value inside the dict
                for v in parsed.values():
                    if isinstance(v, list):
                        parsed = v
                        break
                else:
                    parsed = [parsed]

            if not isinstance(parsed, list):
                parsed = [parsed]

            results = []
            for item in parsed:
                if not isinstance(item, dict):
                    continue
                label = item.get("label", LABEL_NOT)
                if label not in (LABEL_SPAM, LABEL_NOT):
                    label = LABEL_NOT
                results.append({
                    "id": item.get("id"),
                    "label": label,
                    "confidence": float(item.get("confidence", 0.5)),
                    "reason": str(item.get("reason", "")),
                })
            return results

        except (json.JSONDecodeError, KeyError, TypeError) as e:
            print(f"  Parse error (attempt {attempt}/{MAX_RETRIES}): {e}")
            if attempt < MAX_RETRIES:
                time.sleep(RETRY_DELAY_SEC)
        except openai.RateLimitError:
            wait = RETRY_DELAY_SEC * attempt * 2
            print(f"  Rate limited, waiting {wait}s ...")
            time.sleep(wait)
        except openai.APIError as e:
            print(f"  API error (attempt {attempt}/{MAX_RETRIES}): {e}")
            if attempt < MAX_RETRIES:
                time.sleep(RETRY_DELAY_SEC)

    print(f"  FAILED after {MAX_RETRIES} attempts, skipping batch")
    return []


def load_already_labeled() -> set[int]:
    """Return the set of message IDs already in labeled.csv."""
    if not LABELED_CSV.exists():
        return set()
    df = pd.read_csv(LABELED_CSV)
    return set(df["id"].astype(int))


def main() -> None:
    parser = argparse.ArgumentParser(description="Auto-label messages with gpt-4o-mini")
    parser.add_argument("--sample", type=int, default=None,
                        help="Label only a random sample of N messages")
    args = parser.parse_args()

    if not MESSAGES_CSV.exists():
        print(f"ERROR: {MESSAGES_CSV} not found. Run step1_extract.py first.")
        sys.exit(1)

    key = get_openai_key()
    client = openai.OpenAI(api_key=key)

    # Quick auth test
    print("Testing API key ...", flush=True)
    try:
        resp = client.chat.completions.create(
            model=OPENAI_MODEL,
            messages=[{"role": "user", "content": "Reply with the word OK"}],
            max_tokens=5,
        )
        print(f"API key OK (model responded: {resp.choices[0].message.content.strip()})", flush=True)
    except Exception as e:
        print(f"API key FAILED: {e}")
        sys.exit(1)

    messages = pd.read_csv(MESSAGES_CSV)
    print(f"Loaded {len(messages):,} messages from {MESSAGES_CSV}")

    already = load_already_labeled()
    if already:
        print(f"Resuming: {len(already):,} already labeled, skipping those")
        messages = messages[~messages["id"].isin(already)]

    if args.sample and len(messages) > args.sample:
        messages = messages.sample(n=args.sample, random_state=42)
        print(f"Sampling {args.sample:,} messages for quality check")

    todo = messages.to_dict("records")
    total = len(todo)
    print(f"Labeling {total:,} messages in batches of {BATCH_SIZE} ...")

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    write_header = not LABELED_CSV.exists() or not already

    labeled_count = 0
    spam_count = 0
    t0 = time.time()

    with open(LABELED_CSV, "a", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        if write_header:
            writer.writerow(["id", "address", "body", "label", "confidence", "reason"])

        for i in range(0, total, BATCH_SIZE):
            batch = todo[i : i + BATCH_SIZE]
            results = label_batch(client, batch)

            result_map = {r["id"]: r for r in results if r["id"] is not None}
            for row in batch:
                r = result_map.get(row["id"])
                if r:
                    writer.writerow([
                        row["id"],
                        row["address"],
                        row["body"][:200],
                        r["label"],
                        r["confidence"],
                        r["reason"],
                    ])
                    labeled_count += 1
                    if r["label"] == LABEL_SPAM:
                        spam_count += 1

            f.flush()

            done = min(i + BATCH_SIZE, total)
            elapsed = time.time() - t0
            rate = done / elapsed if elapsed > 0 else 0
            eta = (total - done) / rate if rate > 0 else 0
            print(
                f"  {done:,}/{total:,} "
                f"({spam_count} spam so far) "
                f"[{rate:.0f} msg/s, ETA {eta/60:.1f}m]"
            )

    elapsed = time.time() - t0
    print(f"\nDone: {labeled_count:,} labeled in {elapsed:.0f}s")
    print(f"  {spam_count:,} POLITICAL_SPAM / {labeled_count - spam_count:,} NOT")
    print(f"  Output: {LABELED_CSV}")


if __name__ == "__main__":
    main()
