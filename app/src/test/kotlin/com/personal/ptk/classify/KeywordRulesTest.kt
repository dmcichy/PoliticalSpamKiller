package com.personal.ptk.classify

import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class KeywordRulesTest {

    @Test
    @DisplayName("Single keyword match")
    fun singleKeywordMatch() {
        val result = KeywordRules.check("Please vote today!", listOf("vote"))
        assertInstanceOf(Verdict.Kill::class.java, result)
        assertEquals("vote", (result as Verdict.Kill).matchedRule)
    }

    @Test
    @DisplayName("Multi-word phrase match")
    fun multiWordPhraseMatch() {
        val result = KeywordRules.check("Can you chip in \$5?", listOf("chip in"))
        assertInstanceOf(Verdict.Kill::class.java, result)
    }

    @Test
    @DisplayName("Case insensitive match")
    fun caseInsensitiveMatch() {
        val result = KeywordRules.check("DONATE NOW!", listOf("donate"))
        assertInstanceOf(Verdict.Kill::class.java, result)
    }

    @Test
    @DisplayName("No match returns null")
    fun noMatchReturnsNull() {
        val result = KeywordRules.check("Your Amazon delivery arrives tomorrow.", listOf("vote", "donate"))
        assertNull(result)
    }

    @Test
    @DisplayName("Word boundary: 'devotee' does NOT match 'vote'")
    fun wordBoundaryDevotee() {
        val result = KeywordRules.check("She is a devotee of classical music", listOf("vote"))
        assertNull(result)
    }

    @Test
    @DisplayName("Word boundary: 'envoté' does NOT match 'vote'")
    fun wordBoundaryAccented() {
        val result = KeywordRules.check("envoté ce document", listOf("vote"))
        assertNull(result)
    }

    @Test
    @DisplayName("Empty body returns null")
    fun emptyBody() {
        assertNull(KeywordRules.check("", listOf("vote")))
    }

    @Test
    @DisplayName("Blank body returns null")
    fun blankBody() {
        assertNull(KeywordRules.check("   ", listOf("vote")))
    }

    @Test
    @DisplayName("Empty keyword list returns null")
    fun emptyKeywordList() {
        assertNull(KeywordRules.check("vote now!", emptyList()))
    }

    @Test
    @DisplayName("Punctuation does not block match")
    fun punctuationStripped() {
        val result = KeywordRules.check("Will you vote?", listOf("vote"))
        assertInstanceOf(Verdict.Kill::class.java, result)
    }

    @Test
    @DisplayName("First matching keyword wins")
    fun firstMatchWins() {
        val result = KeywordRules.check(
            "Donate to this campaign now!",
            listOf("donate", "campaign")
        )
        assertInstanceOf(Verdict.Kill::class.java, result)
        assertEquals("donate", (result as Verdict.Kill).matchedRule)
    }

    @Test
    @DisplayName("Tokenize preserves accented characters")
    fun tokenizeAccented() {
        val tokens = KeywordRules.tokenize("j'ai envoté mon résumé")
        // Apostrophe is a split character, so "j'ai" becomes "j" and "ai"
        assertEquals(listOf("j", "ai", "envoté", "mon", "résumé"), tokens)
    }

    @Test
    @DisplayName("Tokenize handles multiple punctuation types")
    fun tokenizePunctuation() {
        val tokens = KeywordRules.tokenize("hello, world! foo@bar (test) [ok]")
        assertEquals(listOf("hello", "world", "foo", "bar", "test", "ok"), tokens)
    }
}
