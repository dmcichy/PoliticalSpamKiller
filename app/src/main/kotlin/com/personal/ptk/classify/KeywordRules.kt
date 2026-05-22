package com.personal.ptk.classify

object KeywordRules {

    fun check(body: String, keywords: List<String>): Verdict.Kill? {
        if (body.isBlank() || keywords.isEmpty()) return null

        val normalized = body.lowercase()
        val words = tokenize(normalized)
        val wordSet = words.toSet()

        for (keyword in keywords) {
            val kw = keyword.lowercase().trim()
            if (kw.isBlank()) continue

            if (kw.contains(' ')) {
                // Multi-word phrase: check as substring in normalized body
                if (normalized.contains(kw)) {
                    return Verdict.Kill("KEYWORD", kw)
                }
            } else {
                // Single word: check against tokenized word set (word-boundary match)
                if (kw in wordSet) {
                    return Verdict.Kill("KEYWORD", kw)
                }
            }
        }

        return null
    }

    /**
     * Tokenizes text into words using word boundaries.
     * Strips punctuation but preserves accented characters so that
     * e.g. "envoté" does NOT match "vote".
     */
    fun tokenize(text: String): List<String> {
        return text
            .split(Regex("[\\s,.!?;:\"'()\\[\\]{}|/\\\\<>@#&*^~`]+"))
            .filter { it.isNotBlank() }
            .map { it.trim('-', '_') }
            .filter { it.isNotBlank() }
    }
}
