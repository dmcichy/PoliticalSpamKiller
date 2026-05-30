package com.personal.ptk.classify

class Classifier(
    private val contactChecker: ContactChecker,
    private val ruleProvider: RuleProvider,
    private val mlClassifier: MlClassifier? = null,
    private val isMlEnabled: () -> Boolean = { false }
) {
    suspend fun classify(sender: String, body: String): Verdict {
        if (sender.isBlank()) return Verdict.Allow

        val normalizedSender = normalizeNumber(sender)
        val tag = "Classifier"

        // 1. Contact check — contacts are sacred
        if (contactChecker.isKnown(sender)) {
            android.util.Log.d(tag, "ALLOW contact: $sender")
            return Verdict.Allow
        }

        // 2. User allowlist (numbers)
        if (ruleProvider.isAllowlisted(normalizedSender)) {
            android.util.Log.d(tag, "ALLOW allowlisted: $sender ($normalizedSender)")
            return Verdict.Allow
        }

        // 3. User blocklist (numbers)
        if (ruleProvider.isBlocklisted(normalizedSender)) {
            android.util.Log.d(tag, "KILL blocklist: $sender")
            return Verdict.Kill("BLOCKED_NUMBER", sender)
        }

        // 4. Keyword rules
        val keywords = ruleProvider.getActiveKeywords()
        val kwResult = KeywordRules.check(body, keywords)
        if (kwResult != null) {
            android.util.Log.d(tag, "KILL keyword=${kwResult.matchedRule}: $sender")
            return kwResult
        }

        // 5. Heuristic rules
        val hResult = HeuristicRules.check(sender, body)
        if (hResult != null) {
            android.util.Log.d(tag, "KILL heuristic=${hResult.matchedRule}: $sender")
            return hResult
        }

        // 6. ML classifier (if enabled)
        if (isMlEnabled() && mlClassifier != null) {
            val score = mlClassifier.classify(body)
            if (score >= ML_THRESHOLD) {
                return Verdict.Kill("ML", "score=${"%.3f".format(score)}")
            }
        }

        // 7. Default — allow
        val preview = body.take(40).replace("\n", " ")
        android.util.Log.d(tag, "ALLOW default: $sender ($normalizedSender) body='$preview...'")
        return Verdict.Allow
    }

    companion object {
        private const val ML_THRESHOLD = 0.90f

        fun normalizeNumber(number: String): String {
            val digits = number.filter { it.isDigit() }
            return if (digits.length >= 10) digits.takeLast(10) else digits
        }
    }
}
