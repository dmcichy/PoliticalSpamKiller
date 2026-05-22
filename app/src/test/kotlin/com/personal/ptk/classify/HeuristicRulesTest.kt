package com.personal.ptk.classify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HeuristicRulesTest {

    @Test
    @DisplayName("Shortcode with dollar sign -> DONATION_HEURISTIC")
    fun shortcodeDollarSign() {
        val result = HeuristicRules.check("88022", "Send \$25 now to save America!")
        assertInstanceOf(Verdict.Kill::class.java, result)
        assertEquals("DONATION_HEURISTIC", (result as Verdict.Kill).reason)
    }

    @Test
    @DisplayName("Shortcode with 'donate' -> DONATION_HEURISTIC")
    fun shortcodeDonate() {
        val result = HeuristicRules.check("55555", "Please donate today")
        assertInstanceOf(Verdict.Kill::class.java, result)
        assertEquals("DONATION_HEURISTIC", (result as Verdict.Kill).reason)
    }

    @Test
    @DisplayName("Regular 10-digit number with dollar sign -> no heuristic match")
    fun regularNumberDollarSign() {
        val result = HeuristicRules.check("5559876543", "You owe \$50 for dinner")
        assertNull(result)
    }

    @Test
    @DisplayName("actblue.com domain -> DONATION_PLATFORM")
    fun actblueDomain() {
        val result = HeuristicRules.check("5559876543", "Help us win! https://actblue.com/mycandidate")
        assertInstanceOf(Verdict.Kill::class.java, result)
        assertEquals("DONATION_PLATFORM", (result as Verdict.Kill).reason)
    }

    @Test
    @DisplayName("winred.com domain -> DONATION_PLATFORM")
    fun winredDomain() {
        val result = HeuristicRules.check("5559876543", "Support the cause at winred.com/save")
        assertInstanceOf(Verdict.Kill::class.java, result)
        assertEquals("DONATION_PLATFORM", (result as Verdict.Kill).reason)
    }

    @Test
    @DisplayName("Candidate name + fundraising verb -> CAMPAIGN_HEURISTIC")
    fun campaignHeuristic() {
        val result = HeuristicRules.check("5559876543", "Can you support trump in this critical race?")
        assertInstanceOf(Verdict.Kill::class.java, result)
        assertEquals("CAMPAIGN_HEURISTIC", (result as Verdict.Kill).reason)
    }

    @Test
    @DisplayName("Candidate name without fundraising verb -> no match")
    fun candidateNameAlone() {
        val result = HeuristicRules.check("5559876543", "Did you hear what Trump said today?")
        assertNull(result)
    }

    @Test
    @DisplayName("Fundraising verb without candidate name -> no match")
    fun fundraisingVerbAlone() {
        val result = HeuristicRules.check("5559876543", "Please donate to the local food bank")
        assertNull(result)
    }

    @Test
    @DisplayName("isShortcode detects 5-digit code")
    fun isShortcodeFiveDigit() {
        assertEquals(true, HeuristicRules.isShortcode("88022"))
    }

    @Test
    @DisplayName("isShortcode detects 6-digit code")
    fun isShortcodeSixDigit() {
        assertEquals(true, HeuristicRules.isShortcode("123456"))
    }

    @Test
    @DisplayName("isShortcode rejects 10-digit number")
    fun isShortcodeRejectsTenDigit() {
        assertEquals(false, HeuristicRules.isShortcode("5559876543"))
    }

    @Test
    @DisplayName("isShortcode rejects alpha string")
    fun isShortcodeRejectsAlpha() {
        assertEquals(false, HeuristicRules.isShortcode("HELLO"))
    }

    @Test
    @DisplayName("Empty body -> no match")
    fun emptyBody() {
        assertNull(HeuristicRules.check("88022", ""))
    }

    @Test
    @DisplayName("Platform domain check is case insensitive")
    fun domainCaseInsensitive() {
        val result = HeuristicRules.check("5559876543", "visit ACTBLUE.COM/donate")
        // The body is lowercased in check(), so this should match
        assertInstanceOf(Verdict.Kill::class.java, result)
    }
}
