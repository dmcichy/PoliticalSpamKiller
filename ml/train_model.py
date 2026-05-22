"""
Train a lightweight text classification model for political/donation SMS spam.

Outputs:
  model_output/political_spam_model.tflite  — TFLite model for Android
  model_output/vocab.json                   — word-to-index mapping

Usage:
  cd ml/
  pip install -r requirements.txt
  python train_model.py

If data/training_data.csv exists it is used; otherwise a synthetic starter
dataset is generated for bootstrapping.
"""

import json
import os
import re
from pathlib import Path

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.model_selection import train_test_split

VOCAB_SIZE = 5000
MAX_LEN = 128
EMBEDDING_DIM = 64
OUTPUT_DIR = Path("model_output")
DATA_PATH = Path("data/training_data.csv")


def generate_starter_dataset() -> pd.DataFrame:
    """Generate a synthetic dataset for bootstrapping when no real data exists."""

    spam = [
        "Donate $5 NOW before the FEC deadline! Match 5x at actblue.com/save",
        "URGENT: Trump needs your support. Chip in $25 by midnight!",
        "Will you stand with Democrats? Your $10 contribution is TRIPLE matched!",
        "FINAL NOTICE: The election is in 3 days. Vote for change. Donate now.",
        "Are you with us? Match my $50 donation at winred.com/patriot",
        "Your donation to the RNC will be matched 500%. Give $15 today!",
        "The DNC needs you! Pitch in before the midnight deadline.",
        "MAGA! Support the GOP. Every dollar matched. donate now",
        "Harris needs your help to win. Chip in at actblue.com/harris",
        "Conservative values under attack. Stand with us. $5 matched 10x.",
        "Biden's agenda needs YOUR backing. Contribute $25 today.",
        "POLL ALERT: Are you voting Republican? Donate to keep America great!",
        "The liberal media won't tell you this. Support the campaign. $10 now.",
        "Your $20 contribution QUADRUPLE matched through midnight!",
        "Election day is coming. Make your voice heard. Donate at winred.com",
        "URGENT DEADLINE: FEC reporting deadline in 2 hours. We need $5 from you.",
        "DeSantis 2024! Help us reach our fundraising goal. Chip in $10.",
        "Can you pitch in $5? Your donation is matched 5x by our top donor.",
        "This is your FINAL chance to donate before the polls close.",
        "Republican National Committee: We need 50,000 donors by midnight. You in?",
        "Newsom is fighting for California. Support his campaign today.",
        "WALZ for VP! Stand with the Democratic ticket. Contribute now.",
        "VANCE needs your support for the Senate. Donate at secure.anedot.com/vance",
        "Triple match ACTIVATED! Your $10 becomes $30. Donate at actblue.com",
        "The conservative movement needs you. Gift $25 to the campaign.",
        "BALLOT ALERT: Protect your vote. Donate to election integrity fund.",
        "Are you registered to vote? The deadline is approaching. Act now!",
        "Campaign update: We're $10,000 short. Can you chip in $5?",
        "Your candidate needs you. Every contribution matters. donorbox.org/freedom",
        "IMPEACH NOW! Support the effort. Donate $10 to the cause.",
        "The filibuster must end. Stand with us. $15 matched 3x today only.",
        "POLL: Do you support Trump? Reply YES and donate $5.",
        "Midnight deadline! 2x match on all donations. actblue.com/match",
        "Election integrity under threat. Donate to protect the ballot.",
        "Your $5 gift keeps our campaign alive. Pitch in before it's too late.",
        "GOP ALERT: Liberal spending out of control. Fight back with $10.",
        "Democrat voter? Your voice matters. Donate $20 to flip the Senate.",
        "MATCH EXPIRING: 5x match ends at midnight. Don't miss out!",
        "The campaign trail needs fuel. Your $25 contribution is critical.",
        "Conservative PAC: Emergency fundraising. We need $100k by Friday.",
    ]

    legit = [
        "Your Amazon package has shipped. Track at amazon.com/track/ABC123",
        "Your verification code is 483921. Do not share this code.",
        "Hi! Are we still on for dinner tonight at 7?",
        "Your bank statement is ready. Log in to view.",
        "Appointment reminder: Dr. Smith tomorrow at 2:30 PM",
        "Your Uber is arriving in 3 minutes. Toyota Camry, plate ABC 1234.",
        "Flash sale! 50% off all shoes today only at FootLocker.",
        "Your prescription is ready for pickup at CVS Pharmacy.",
        "Flight UA-4521 is delayed 45 minutes. New departure: 3:15 PM.",
        "Happy birthday! Hope you have an amazing day!",
        "Hey, can you pick up milk on the way home?",
        "Your credit card ending in 4532 was charged $45.99 at Target.",
        "Meeting moved to 3 PM. Same conference room.",
        "Your order #8834 has been delivered. Rate your experience.",
        "Mom: Call me when you get a chance. Love you!",
        "Weather alert: Severe thunderstorm warning until 8 PM.",
        "Your Netflix payment of $15.99 was processed.",
        "School closed tomorrow due to snow. Stay safe!",
        "Your car service appointment is confirmed for Saturday 9 AM.",
        "Pizza is here! I left it on the counter.",
        "Congrats on the promotion! Well deserved.",
        "Your refund of $29.99 has been processed to your account.",
        "Don't forget: team lunch at noon in the break room.",
        "Your mobile data usage has reached 80% of your plan.",
        "Welcome to T-Mobile! Your new plan is now active.",
        "Your gym membership renews on the 15th. $30/month.",
        "Package delivery attempted. Will retry tomorrow.",
        "Hey! Want to grab coffee this weekend?",
        "Your Costco order is ready for pickup. Bay 12.",
        "Traffic alert: accident on I-95 northbound. Expect delays.",
        "Your hotel reservation is confirmed. Check-in after 3 PM.",
        "Happy anniversary! 10 years already. Time flies!",
        "Reminder: dentist appointment Thursday at 10 AM.",
        "Your transfer of $200 to savings was completed.",
        "Game night Friday at my place. Bring snacks!",
        "Your auto insurance premium is due. $145.00 by March 1.",
        "Power outage reported in your area. Estimated restore: 6 PM.",
        "Hey, can you send me that recipe from last night?",
        "Your child was marked absent today. Please contact the school.",
        "Oil change due at 75,000 miles. Schedule at your dealer.",
    ]

    data = [(text, 1) for text in spam] + [(text, 0) for text in legit]
    df = pd.DataFrame(data, columns=["text", "label"])
    return df


def clean_text(text: str) -> str:
    text = text.lower()
    text = re.sub(r"[^a-z0-9\s]", "", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def build_vocab(texts: list[str], max_vocab: int) -> dict[str, int]:
    word_counts: dict[str, int] = {}
    for text in texts:
        for word in text.split():
            word_counts[word] = word_counts.get(word, 0) + 1

    sorted_words = sorted(word_counts.items(), key=lambda x: -x[1])
    # 0 = padding, 1 = OOV
    vocab = {word: idx + 2 for idx, (word, _) in enumerate(sorted_words[:max_vocab - 2])}
    return vocab


def texts_to_sequences(texts: list[str], vocab: dict[str, int], max_len: int) -> np.ndarray:
    sequences = []
    for text in texts:
        seq = [vocab.get(word, 1) for word in text.split()][:max_len]
        padded = seq + [0] * (max_len - len(seq))
        sequences.append(padded)
    return np.array(sequences, dtype=np.int32)


def main():
    if DATA_PATH.exists():
        print(f"Loading training data from {DATA_PATH}")
        df = pd.read_csv(DATA_PATH)
    else:
        print("No training_data.csv found — generating synthetic starter dataset")
        df = generate_starter_dataset()
        DATA_PATH.parent.mkdir(parents=True, exist_ok=True)
        df.to_csv(DATA_PATH, index=False)
        print(f"Saved {len(df)} examples to {DATA_PATH}")

    print(f"Dataset: {len(df)} examples ({df['label'].sum()} spam, {(1 - df['label']).sum()} legit)")

    df["clean"] = df["text"].apply(clean_text)

    vocab = build_vocab(df["clean"].tolist(), VOCAB_SIZE)
    print(f"Vocabulary size: {len(vocab) + 2} (including PAD and OOV)")

    X = texts_to_sequences(df["clean"].tolist(), vocab, MAX_LEN)
    y = df["label"].values.astype(np.float32)

    X_train, X_val, y_train, y_val = train_test_split(X, y, test_size=0.2, random_state=42)

    model = tf.keras.Sequential([
        tf.keras.layers.Embedding(len(vocab) + 2, EMBEDDING_DIM, input_length=MAX_LEN),
        tf.keras.layers.GlobalAveragePooling1D(),
        tf.keras.layers.Dense(32, activation="relu"),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(1, activation="sigmoid"),
    ])

    model.compile(optimizer="adam", loss="binary_crossentropy", metrics=["accuracy"])
    model.summary()

    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=30,
        batch_size=16,
        verbose=1,
    )

    val_loss, val_acc = model.evaluate(X_val, y_val, verbose=0)
    print(f"\nValidation accuracy: {val_acc:.4f}")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    # Convert to TFLite
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()

    tflite_path = OUTPUT_DIR / "political_spam_model.tflite"
    tflite_path.write_bytes(tflite_model)
    print(f"TFLite model saved: {tflite_path} ({len(tflite_model) / 1024:.1f} KB)")

    # Save vocabulary
    vocab_path = OUTPUT_DIR / "vocab.json"
    with open(vocab_path, "w") as f:
        json.dump(vocab, f)
    print(f"Vocabulary saved: {vocab_path}")

    # Copy to Android assets directory
    assets_dir = Path("../app/src/main/assets")
    assets_dir.mkdir(parents=True, exist_ok=True)

    (assets_dir / "political_spam_model.tflite").write_bytes(tflite_model)
    with open(assets_dir / "vocab.json", "w") as f:
        json.dump(vocab, f)
    print(f"Copied model and vocab to {assets_dir}")


if __name__ == "__main__":
    main()
