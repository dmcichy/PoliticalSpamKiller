package com.personal.ptk.classify

class Classifier(
    private val contactChecker: ContactChecker,
    private val ruleProvider: RuleProvider,
    private val mlClassifier: MlClassifier? = null,
    private val isMlEnabled: () -> Boolean = { false },
    private val isNuclearMode: () -> Boolean = { false }
) {
    suspend fun classify(sender: String, body: String): Verdict {
        if (sender.isBlank()) return Verdict.Allow

        // 1. Contact check — contacts are sacred
        if (contactChecker.isKnown(sender)) return Verdict.Allow

        // 2. User allowlist (numbers)
        val normalizedSender = normalizeNumber(sender)
        if (ruleProvider.isAllowlisted(normalizedSender)) return Verdict.Allow

        // 3. User blocklist (numbers)
        if (ruleProvider.isBlocklisted(normalizedSender)) {
            return Verdict.Kill("BLOCKED_NUMBER", sender)
        }

        // 4. Keyword rules
        val keywords = ruleProvider.getActiveKeywords()
        KeywordRules.check(body, keywords)?.let { return it }

        // 5. Heuristic rules
        HeuristicRules.check(sender, body, nuclearMode = isNuclearMode())?.let { return it }

        // 6. ML classifier (if enabled)
        if (isMlEnabled() && mlClassifier != null) {
            val score = mlClassifier.classify(body)
            if (score >= ML_THRESHOLD) {
                return Verdict.Kill("ML", "score=${"%.3f".format(score)}")
            }
        }

        // 7. Default — allow
        return Verdict.Allow
    }

    companion object {
        private const val ML_THRESHOLD = 0.85f

        fun normalizeNumber(number: String): String {
            val digits = number.filter { it.isDigit() }
            return if (digits.length >= 10) digits.takeLast(10) else digits
        }
    }
}
