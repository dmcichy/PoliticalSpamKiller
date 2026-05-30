package com.personal.ptk.classify

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ClassifierTest {

    private lateinit var classifier: Classifier

    private val fakeContacts = FakeContactChecker()
    private val fakeRules = FakeRuleProvider()

    @BeforeEach
    fun setup() {
        classifier = Classifier(
            contactChecker = fakeContacts,
            ruleProvider = fakeRules,
            mlClassifier = null,
            isMlEnabled = { false }
        )
    }

    @Test
    @DisplayName("Text from contact with 'donate now' -> ALLOW")
    fun contactBypassOverridesKeywords() = runTest {
        val c = Classifier(
            contactChecker = FakeContactChecker(setOf("+15551234567")),
            ruleProvider = FakeRuleProvider(keywords = DefaultKeywords.ALL),
            mlClassifier = null
        )
        val result = c.classify("+15551234567", "Please donate now! Trump needs your $5 by midnight!")
        assertInstanceOf(Verdict.Allow::class.java, result)
    }

    @Test
    @DisplayName("Text from contact containing every keyword -> ALLOW")
    fun contactBypassEvenWithAllKeywords() = runTest {
        val body = DefaultKeywords.ALL.joinToString(" ")
        val c = Classifier(
            contactChecker = FakeContactChecker(setOf("5551234567")),
            ruleProvider = FakeRuleProvider(keywords = DefaultKeywords.ALL)
        )
        val result = c.classify("5551234567", body)
        assertInstanceOf(Verdict.Allow::class.java, result)
    }

    @Test
    @DisplayName("Text from shortcode 88022 with 'Trump needs $5 by midnight' -> KILL(KEYWORD)")
    fun shortcodeWithPoliticalContent() = runTest {
        val c = Classifier(
            contactChecker = fakeContacts,
            ruleProvider = FakeRuleProvider(keywords = DefaultKeywords.ALL)
        )
        val result = c.classify("88022", "Trump needs \$5 by midnight")
        assertInstanceOf(Verdict.Kill::class.java, result)
    }

    @Test
    @DisplayName("Random 10-digit sender saying 'your package is delayed' -> ALLOW")
    fun legitimateMessageAllowed() = runTest {
        val c = Classifier(
            contactChecker = fakeContacts,
            ruleProvider = FakeRuleProvider(keywords = DefaultKeywords.ALL)
        )
        val result = c.classify("5559876543", "Your package is delayed. Expected delivery tomorrow.")
        assertInstanceOf(Verdict.Allow::class.java, result)
    }

    @Test
    @DisplayName("Random sender saying 'stand with us, chip in $25' -> KILL(KEYWORD)")
    fun donationSolicitationKilled() = runTest {
        val c = Classifier(
            contactChecker = fakeContacts,
            ruleProvider = FakeRuleProvider(keywords = DefaultKeywords.ALL)
        )
        val result = c.classify("5559876543", "Stand with us, chip in \$25 before the midnight deadline!")
        val kill = assertInstanceOf(Verdict.Kill::class.java, result)
        assertEquals("KEYWORD", kill.reason)
    }

    @Test
    @DisplayName("Allowlisted number always passes")
    fun allowlistedNumberPasses() = runTest {
        val c = Classifier(
            contactChecker = fakeContacts,
            ruleProvider = FakeRuleProvider(
                allowlisted = setOf("5559876543"),
                keywords = DefaultKeywords.ALL
            )
        )
        val result = c.classify("5559876543", "VOTE NOW! DONATE! TRUMP!")
        assertInstanceOf(Verdict.Allow::class.java, result)
    }

    @Test
    @DisplayName("Blocklisted number always killed")
    fun blocklistedNumberKilled() = runTest {
        val c = Classifier(
            contactChecker = fakeContacts,
            ruleProvider = FakeRuleProvider(blocklisted = setOf("5559876543"))
        )
        val result = c.classify("5559876543", "Hey, want to grab lunch?")
        val kill = assertInstanceOf(Verdict.Kill::class.java, result)
        assertEquals("BLOCKED_NUMBER", kill.reason)
    }

    @Test
    @DisplayName("Case insensitive: 'VOTE NOW' -> KILL")
    fun caseInsensitiveKeywordMatch() = runTest {
        val c = Classifier(
            contactChecker = fakeContacts,
            ruleProvider = FakeRuleProvider(keywords = listOf("vote"))
        )
        val result = c.classify("5559876543", "VOTE NOW in the upcoming election!")
        assertInstanceOf(Verdict.Kill::class.java, result)
    }

    @Test
    @DisplayName("'envoté' does NOT match 'vote' (word boundary)")
    fun accentedWordDoesNotFalsePositive() = runTest {
        val c = Classifier(
            contactChecker = fakeContacts,
            ruleProvider = FakeRuleProvider(keywords = listOf("vote"))
        )
        val result = c.classify("5559876543", "J'ai envoté mon document par email")
        assertInstanceOf(Verdict.Allow::class.java, result)
    }

    @Test
    @DisplayName("Empty body -> ALLOW")
    fun emptyBodyAllowed() = runTest {
        val c = Classifier(
            contactChecker = fakeContacts,
            ruleProvider = FakeRuleProvider(keywords = DefaultKeywords.ALL)
        )
        val result = c.classify("5559876543", "")
        assertInstanceOf(Verdict.Allow::class.java, result)
    }

    @Test
    @DisplayName("Empty sender -> ALLOW")
    fun emptySenderAllowed() = runTest {
        val result = classifier.classify("", "Vote now!")
        assertInstanceOf(Verdict.Allow::class.java, result)
    }

    @Test
    @DisplayName("No matching rules -> ALLOW (default)")
    fun defaultAllow() = runTest {
        val result = classifier.classify("5559876543", "Lunch at noon?")
        assertInstanceOf(Verdict.Allow::class.java, result)
    }

    // --- Test doubles ---

    private class FakeContactChecker(
        private val known: Set<String> = emptySet()
    ) : ContactChecker {
        override fun isKnown(sender: String): Boolean {
            val normalized = Classifier.normalizeNumber(sender)
            return known.any { Classifier.normalizeNumber(it) == normalized }
        }
    }

    private class FakeRuleProvider(
        private val allowlisted: Set<String> = emptySet(),
        private val blocklisted: Set<String> = emptySet(),
        private val keywords: List<String> = emptyList()
    ) : RuleProvider {
        override suspend fun isAllowlisted(number: String): Boolean {
            val n = Classifier.normalizeNumber(number)
            return allowlisted.any { Classifier.normalizeNumber(it) == n }
        }

        override suspend fun isBlocklisted(number: String): Boolean {
            val n = Classifier.normalizeNumber(number)
            return blocklisted.any { Classifier.normalizeNumber(it) == n }
        }

        override suspend fun getActiveKeywords(): List<String> = keywords
    }
}
