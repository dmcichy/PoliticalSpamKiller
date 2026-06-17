package com.personal.ptk.classify

import java.util.Locale

object KeywordRules {

    private val URL_REGEX = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)

    fun check(body: String, keywords: List<String>): Verdict.Kill? {
        if (body.isBlank() || keywords.isEmpty()) return null

        val normalized = collapseAndLower(body)

        for (keyword in keywords) {
            val kw = keyword.lowercase(Locale.ROOT).trim()
            if (kw.isBlank()) continue

            if (kw.contains(' ')) {
                // Multi-word phrase: case-insensitive substring match on both the
                // pre-lowercased normalized body AND the original body as a safety net,
                // so unicode variants that survive lowercasing are still caught.
                if (normalized.contains(kw, ignoreCase = true) ||
                    body.contains(kw, ignoreCase = true)
                ) {
                    return Verdict.Kill("KEYWORD", kw)
                }
            } else {
                // Leading \b prevents mid-word matches ("pac" won't hit "impact", "poll" won't
                // hit "Apollo"). No trailing \b so plurals/suffixes still match: "lawmaker"
                // catches "Lawmakers", "trump" catches "Trump's", etc.
                val pattern = Regex("\\b${Regex.escape(kw)}", RegexOption.IGNORE_CASE)
                if (pattern.containsMatchIn(normalized) || pattern.containsMatchIn(body)) {
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
                val kw = keyword.lowercase(Locale.ROOT).trim()
                if (kw.isBlank()) continue
                if (urlDomains.any { it.contains(kw, ignoreCase = true) }) {
                    return Verdict.Kill("KEYWORD_URL", kw)
                }
            }
        }

        return null
    }

    fun collapseAndLower(text: String): String = collapseAbbreviations(text.lowercase(Locale.ROOT))

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
