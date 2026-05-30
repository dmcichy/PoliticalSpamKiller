# ml-spam-training

Offline dev tool for building PoliticalTextKiller's on-device TFLite
classifier. Uses LLM distillation: a cloud LLM (gpt-4o-mini) labels your
real SMS archive once, then a small model is trained on those labels and
shipped on-device — private, free, milliseconds.

## Prerequisites

- Python 3.10+
- `pip install -r requirements.txt`
- Doppler CLI configured (`doppler me` should succeed)
- Phone connected via USB (`D:\adb\adb.exe devices` shows your device)

## Pipeline

### Step 1: Extract messages from SMS Backup & Restore XML

```
python step1_extract.py --pull          # adb-pull + extract
python step1_extract.py                 # extract already-pulled XML
python step1_extract.py --pull --limit 500  # quick test
```

Output: `data/messages.csv`

### Step 2: Auto-label with gpt-4o-mini

```
doppler run --project mycredentials --config prd_personal -- python step2_label.py --sample 500
doppler run --project mycredentials --config prd_personal -- python step2_label.py
```

Output: `data/labeled.csv` (resumable — rerun to continue where you left off)

### Step 3: Analyze quality

```
python step3_analyze.py
```

Prints spam/ham counts, confidence histogram, borderline samples.

### Step 4: Train (future)

After validating data quality, train a Keras model and export to TFLite.
