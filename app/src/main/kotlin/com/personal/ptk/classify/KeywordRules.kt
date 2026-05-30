package com.personal.ptk.classify

object KeywordRules {

    private val URL_REGEX = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)

    fun check(body: String, keywords: List<String>): Verdict.Kill? {
        if (body.isBlank() || keywords.isEmpty()) return null

        val normalized = collapseAndLower(body)
        val words = tokenize(normalized)
        val wordSet = words.toSet()

        for (keyword in keywords) {
            val kw = keyword.lowercase().trim()
            if (kw.isBlank()) continue

            if (kw.contains(' ')) {
                // Multi-word phrase: substring match on full body
                if (normalized.contains(kw)) {
                    return Verdict.Kill("KEYWORD", kw)
                }
            } else {
                // Single word: word-boundary match via regex
                // This prevents "pac" from matching "package" or "poll" matching "Apollo"
                val pattern = Regex("\\b${Regex.escape(kw)}\\b")
                if (pattern.containsMatchIn(normalized)) {
                    return Verdict.Kill("KEYWORD", kw)
                }
            }
        }

        // URL domain check: substring matching in URLs is fine because
        // political terms in domains (e.g. "repgop.co") are intentional
        val urlDomains = URL_REGEX.findAll(normalized).map { match ->
            match.value
                .removePrefix("https://").removePrefix("http://")
                .substringBefore("/").substringBefore("?")
        }.toList()

        if (urlDomains.isNotEmpty()) {
            for (keyword in keywords) {
                val kw = keyword.lowercase().trim()
                if (kw.isBlank()) continue
                if (urlDomains.any { it.contains(kw) }) {
                    return Verdict.Kill("KEYWORD_URL", kw)
                }
            }
        }

        return null
    }

    fun collapseAndLower(text: String): String = collapseAbbreviations(text.lowercase())

    private fun collapseAbbreviations(text: String): String {
        var result = text
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\uFEFF", "")

        result = Regex("\\b(([a-z])\\.){2,}([a-z])?\\b", RegexOption.IGNORE_CASE)
            .replace(result) { match ->
                match.value.replace(".", "")
            }

        return result
    }

    fun tokenize(text: String): List<String> {
        return text
            .split(Regex("[\\s,.!?;:\"'()\\[\\]{}|/\\\\<>@#&*^~`]+"))
            .filter { it.isNotBlank() }
            .map { it.trim('-', '_') }
            .filter { it.isNotBlank() }
    }
}
