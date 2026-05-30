"""
Step 6: Train a small text classifier and export to TFLite.

Reads data/labeled.csv (human-reviewed), trains a Keras model optimised
for HIGH PRECISION (minimal false positives), and exports:
  - data/political_spam_model.tflite   (quantised, ~200KB)
  - data/vocab.json                    (token-to-index mapping)
  - data/training_report.txt           (metrics summary)

The model architecture is intentionally tiny for on-device inference:
  Embedding(VOCAB_SIZE, 32) -> GlobalAveragePooling -> Dense(32) -> Dense(1, sigmoid)

Usage:
    python step6_train.py
"""

import csv
import io
import json
import re
import sys
from collections import Counter
from pathlib import Path

if sys.stdout.encoding != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

import numpy as np

from config import LABELED_CSV, DATA_DIR, LABEL_SPAM, LABEL_NOT

# ── Hyperparameters ──────────────────────────────────────────────────
VOCAB_SIZE = 8000
MAX_SEQ_LEN = 128
EMBED_DIM = 32
HIDDEN_DIM = 32
EPOCHS = 15
BATCH_SIZE = 64
VALIDATION_SPLIT = 0.15
# Threshold tuned for high precision — we'd rather miss some spam
# than auto-delete a legitimate message
PRECISION_TARGET_THRESHOLD = 0.90

OUT_TFLITE = DATA_DIR / "political_spam_model.tflite"
OUT_VOCAB = DATA_DIR / "vocab.json"
OUT_REPORT = DATA_DIR / "training_report.txt"


def tokenize(text: str) -> list[str]:
    """Lowercase, strip URLs/phone numbers, split on non-alpha."""
    text = text.lower()
    text = re.sub(r"https?://\S+", " _URL_ ", text)
    text = re.sub(r"\b\d{10,}\b", " _PHONE_ ", text)
    text = re.sub(r"[^a-z_]", " ", text)
    return text.split()


def build_vocab(texts: list[str], max_size: int) -> dict[str, int]:
    """Build word->index mapping from corpus. Index 0 = padding, 1 = OOV."""
    counter: Counter = Counter()
    for t in texts:
        counter.update(tokenize(t))
    vocab = {"<PAD>": 0, "<OOV>": 1}
    for word, _ in counter.most_common(max_size - 2):
        vocab[word] = len(vocab)
    return vocab


def encode(text: str, vocab: dict[str, int], max_len: int) -> list[int]:
    tokens = tokenize(text)[:max_len]
    oov = vocab["<OOV>"]
    encoded = [vocab.get(t, oov) for t in tokens]
    # Pad to max_len
    encoded += [0] * (max_len - len(encoded))
    return encoded


def main() -> None:
    # Defer heavy imports so script fails fast on config issues
    import tensorflow as tf
    from sklearn.model_selection import train_test_split
    from sklearn.metrics import classification_report, precision_recall_curve

    print("Loading labeled data ...", flush=True)
    texts, labels = [], []
    with open(LABELED_CSV, encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            body = row.get("body", "").strip()
            if not body:
                continue
            texts.append(body)
            labels.append(1 if row["label"] == LABEL_SPAM else 0)

    labels_arr = np.array(labels, dtype=np.float32)
    spam_count = int(labels_arr.sum())
    not_count = len(labels_arr) - spam_count
    print(f"  {len(texts):,} messages ({spam_count:,} spam, {not_count:,} not)")

    # ── Build vocabulary ─────────────────────────────────────────────
    print("Building vocabulary ...", flush=True)
    vocab = build_vocab(texts, VOCAB_SIZE)
    print(f"  Vocab size: {len(vocab):,}")

    # ── Encode texts ─────────────────────────────────────────────────
    print("Encoding texts ...", flush=True)
    X = np.array([encode(t, vocab, MAX_SEQ_LEN) for t in texts], dtype=np.int32)
    y = labels_arr

    # ── Train/test split (stratified) ────────────────────────────────
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=VALIDATION_SPLIT, random_state=42, stratify=y
    )
    print(f"  Train: {len(X_train):,} | Test: {len(X_test):,}", flush=True)

    # ── Class weights (penalise false positives more) ────────────────
    # Higher weight on class 0 (NOT) means the model pays more for
    # misclassifying a legitimate message as spam
    weight_not = 1.0
    weight_spam = not_count / spam_count * 0.5  # down-weight spam recall
    class_weight = {0: weight_not, 1: weight_spam}
    print(f"  Class weights: NOT={weight_not:.2f}, SPAM={weight_spam:.2f}", flush=True)

    # ── Build model ──────────────────────────────────────────────────
    print("Building model ...", flush=True)
    model = tf.keras.Sequential([
        tf.keras.layers.Embedding(
            input_dim=len(vocab), output_dim=EMBED_DIM,
            input_length=MAX_SEQ_LEN
        ),
        tf.keras.layers.GlobalAveragePooling1D(),
        tf.keras.layers.Dense(HIDDEN_DIM, activation="relu"),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(1, activation="sigmoid"),
    ])
    model.compile(
        optimizer="adam",
        loss="binary_crossentropy",
        metrics=["accuracy"],
    )
    model.summary()

    # ── Train ────────────────────────────────────────────────────────
    print(f"\nTraining for {EPOCHS} epochs ...", flush=True)
    history = model.fit(
        X_train, y_train,
        epochs=EPOCHS,
        batch_size=BATCH_SIZE,
        validation_data=(X_test, y_test),
        class_weight=class_weight,
        verbose=1,
    )

    # ── Evaluate ─────────────────────────────────────────────────────
    print("\nEvaluating ...", flush=True)
    y_probs = model.predict(X_test, verbose=0).flatten()

    # Find threshold that gives >= 95% precision
    precisions, recalls, thresholds = precision_recall_curve(y_test, y_probs)
    best_thresh = PRECISION_TARGET_THRESHOLD
    for p, r, t in zip(precisions, recalls, thresholds):
        if p >= 0.95:
            best_thresh = t
            break

    y_pred = (y_probs >= best_thresh).astype(int)
    report = classification_report(y_test, y_pred, target_names=["NOT", "SPAM"])
    print(f"\nClassification Report (threshold={best_thresh:.3f}):")
    print(report)

    # Also show at the planned 0.90 threshold
    y_pred_90 = (y_probs >= 0.90).astype(int)
    report_90 = classification_report(y_test, y_pred_90, target_names=["NOT", "SPAM"])
    print(f"\nClassification Report (threshold=0.90):")
    print(report_90)

    # ── Export TFLite ────────────────────────────────────────────────
    print("Exporting TFLite model ...", flush=True)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()
    OUT_TFLITE.write_bytes(tflite_model)
    tflite_kb = len(tflite_model) / 1024
    print(f"  Model: {OUT_TFLITE} ({tflite_kb:.0f} KB)")

    # ── Export vocab ─────────────────────────────────────────────────
    OUT_VOCAB.write_text(json.dumps(vocab, ensure_ascii=False), encoding="utf-8")
    print(f"  Vocab: {OUT_VOCAB}")

    # ── Save report ──────────────────────────────────────────────────
    report_text = (
        f"Training Report\n"
        f"{'='*50}\n"
        f"Dataset: {len(texts):,} messages ({spam_count:,} spam, {not_count:,} not)\n"
        f"Train/Test split: {len(X_train):,} / {len(X_test):,}\n"
        f"Vocab size: {len(vocab):,}\n"
        f"Max sequence length: {MAX_SEQ_LEN}\n"
        f"Epochs: {EPOCHS}\n"
        f"Class weights: NOT={weight_not:.2f}, SPAM={weight_spam:.2f}\n"
        f"\nPrecision-tuned threshold: {best_thresh:.3f}\n"
        f"\n{report}\n"
        f"\nAt fixed 0.90 threshold:\n{report_90}\n"
        f"\nTFLite model size: {tflite_kb:.0f} KB\n"
    )
    OUT_REPORT.write_text(report_text, encoding="utf-8")
    print(f"  Report: {OUT_REPORT}")

    print(f"\nDone. Recommended auto-delete threshold: {best_thresh:.3f}")
    print("Copy political_spam_model.tflite and vocab.json into app/src/main/assets/")


if __name__ == "__main__":
    main()
