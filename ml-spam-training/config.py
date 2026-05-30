"""
Shared configuration for the ml-spam-training pipeline.

Secrets are injected at runtime via Doppler:
    doppler run --project mycredentials --config prd_personal -- python step2_label.py
The env var LLM_OPENAI_4O_MINI contains the OpenAI API key.
"""

import os
from pathlib import Path

# ── Paths ─────────────────────────────────────────────────────────────
PROJECT_ROOT = Path(__file__).resolve().parent
DATA_DIR = PROJECT_ROOT / "data"

BACKUP_XML_DEVICE_PATH = "/storage/emulated/0/BakupSMSCallLog/sms-20260201004700.xml"
BACKUP_XML_LOCAL = DATA_DIR / "sms-20260201004700.xml"

MESSAGES_CSV = DATA_DIR / "messages.csv"
LABELED_CSV = DATA_DIR / "labeled.csv"

ADB_PATH = r"D:\adb\adb.exe"

# ── Labeling ──────────────────────────────────────────────────────────
OPENAI_MODEL = "gpt-4o-mini"
OPENAI_API_KEY_ENV = "LLM_OPENAI_4O_MINI"

BATCH_SIZE = 30          # messages per API call
MAX_RETRIES = 3
RETRY_DELAY_SEC = 5

# ── Labels ────────────────────────────────────────────────────────────
LABEL_SPAM = "POLITICAL_SPAM"
LABEL_NOT = "NOT"

def get_openai_key() -> str:
    raw = os.environ.get(OPENAI_API_KEY_ENV)
    if not raw:
        raise RuntimeError(
            f"Missing env var {OPENAI_API_KEY_ENV}. "
            f"Run via: doppler run --project mycredentials "
            f"--config prd_personal -- python <script>"
        )
    # Doppler stores it as a JSON object with the actual key in "Password"
    if raw.strip().startswith("{"):
        import json
        try:
            obj = json.loads(raw)
            return obj["Password"]
        except (json.JSONDecodeError, KeyError):
            pass
    return raw
