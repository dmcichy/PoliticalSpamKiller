"""
Step 1: Extract SMS text bodies from an SMS Backup & Restore XML file.

Streams the (potentially huge) XML via iterparse so memory stays flat even
for multi-GB files with base64-encoded MMS media.  Only <sms> elements are
extracted; <mms> elements are skipped entirely.

Output: data/messages.csv  (columns: id, address, date, type, body)

Usage:
    python step1_extract.py                    # parse already-pulled XML
    python step1_extract.py --pull             # adb-pull from device first
    python step1_extract.py --pull --limit 500 # pull + only first N msgs
"""

import argparse
import csv
import html
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from config import (
    ADB_PATH,
    BACKUP_XML_DEVICE_PATH,
    BACKUP_XML_LOCAL,
    DATA_DIR,
    MESSAGES_CSV,
)


def pull_from_device() -> None:
    """adb pull the backup XML from the phone to DATA_DIR."""
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    print(f"Pulling {BACKUP_XML_DEVICE_PATH} -> {BACKUP_XML_LOCAL}")
    print("(4.78 GB - this will take a few minutes over USB) ...")
    t0 = time.time()
    subprocess.run(
        [ADB_PATH, "pull", BACKUP_XML_DEVICE_PATH, str(BACKUP_XML_LOCAL)],
        check=True,
    )
    elapsed = time.time() - t0
    size_gb = BACKUP_XML_LOCAL.stat().st_size / (1024 ** 3)
    print(f"Done: {size_gb:.2f} GB in {elapsed:.0f}s")


def extract(limit: int | None = None) -> None:
    """Stream-parse the XML and write messages.csv."""
    if not BACKUP_XML_LOCAL.exists():
        print(f"ERROR: {BACKUP_XML_LOCAL} not found. Run with --pull first.")
        sys.exit(1)

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    out_path = MESSAGES_CSV

    count = 0
    skipped_blank = 0

    with open(out_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["id", "address", "date", "type", "body"])

        context = ET.iterparse(str(BACKUP_XML_LOCAL), events=("end",))
        for _event, elem in context:
            if elem.tag != "sms":
                elem.clear()
                continue

            body_raw = elem.get("body", "")
            body = html.unescape(body_raw).strip()
            address = elem.get("address", "").strip()
            date = elem.get("date", "")
            msg_type = elem.get("type", "")

            elem.clear()

            if not body or not address:
                skipped_blank += 1
                continue

            count += 1
            writer.writerow([count, address, date, msg_type, body])

            if count % 5000 == 0:
                print(f"  extracted {count:,} messages ...")

            if limit and count >= limit:
                break

    print(f"\nExtracted {count:,} messages -> {out_path}")
    print(f"Skipped {skipped_blank:,} blank/addressless entries")
    print(f"File size: {out_path.stat().st_size / (1024*1024):.1f} MB")


def main() -> None:
    parser = argparse.ArgumentParser(description="Extract SMS bodies from backup XML")
    parser.add_argument("--pull", action="store_true", help="adb-pull the XML first")
    parser.add_argument("--limit", type=int, default=None, help="Stop after N messages")
    args = parser.parse_args()

    if args.pull:
        pull_from_device()

    extract(limit=args.limit)


if __name__ == "__main__":
    main()
