package com.personal.ptk.util

import com.personal.ptk.classify.DefaultKeywords
import com.personal.ptk.classify.KeywordRules

object KeywordSuggester {

    /**
     * Analyzes killed message bodies and suggests words that appear frequently
     * but are not already in the active keyword list.
     *
     * @param bodies list of message bodies from the vault (last 30 days)
     * @param existingKeywords set of keywords already in the blocklist
     * @param maxSuggestions maximum number of suggestions to return
     * @return list of suggested keywords, ordered by frequency descending
     */
    fun suggest(
        bodies: List<String>,
        existingKeywords: Set<String>,
        maxSuggestions: Int = 10
    ): List<String> {
        if (bodies.isEmpty()) return emptyList()

        val existingLower = existingKeywords.map { it.lowercase() }.toSet()

        val wordCounts = mutableMapOf<String, Int>()
        for (body in bodies) {
            val words = KeywordRules.tokenize(body.lowercase()).toSet()
            for (word in words) {
                if (word.length >= 3 &&
                    word !in DefaultKeywords.COMMON_STOPWORDS &&
                    word !in existingLower &&
                    !word.all { it.isDigit() }
                ) {
                    wordCounts[word] = (wordCounts[word] ?: 0) + 1
                }
            }
        }

        // Only suggest words that appear in at least 3 different messages
        return wordCounts
            .filter { it.value >= 3 }
            .entries
            .sortedByDescending { it.value }
            .take(maxSuggestions)
            .map { it.key }
    }
}
