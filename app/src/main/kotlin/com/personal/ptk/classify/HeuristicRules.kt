package com.personal.ptk.classify

object HeuristicRules {

    private val SHORTCODE_PATTERN = Regex("^\\d{5,6}$")

    fun check(sender: String, body: String, nuclearMode: Boolean = false): Verdict.Kill? {
        val normalized = body.lowercase()

        checkCampaignHeuristic(normalized)?.let { return it }

        if (nuclearMode) {
            checkDonationPlatform(normalized)?.let { return it }
            checkShortcodeDonation(sender, normalized)?.let { return it }
        }

        return null
    }

    private fun checkDonationPlatform(body: String): Verdict.Kill? {
        for (domain in DefaultKeywords.FUNDRAISING_DOMAINS) {
            if (body.contains(domain)) {
                return Verdict.Kill("DONATION_PLATFORM", domain)
            }
        }
        return null
    }

    private fun checkShortcodeDonation(sender: String, body: String): Verdict.Kill? {
        if (!SHORTCODE_PATTERN.matches(sender.trim())) return null

        for (indicator in DefaultKeywords.DONATION_INDICATORS) {
            if (indicator == "$") {
                if (body.contains("$") || Regex("\\$\\d").containsMatchIn(body)) {
                    return Verdict.Kill("DONATION_HEURISTIC", "shortcode+dollar")
                }
            } else if (body.contains(indicator)) {
                return Verdict.Kill("DONATION_HEURISTIC", "shortcode+$indicator")
            }
        }
        return null
    }

    private fun checkCampaignHeuristic(body: String): Verdict.Kill? {
        val candidateNames = listOf(
            "trump", "biden", "harris", "vance", "walz", "desantis", "newsom"
        )

        val hasCandidate = candidateNames.any { body.contains(it) }
        if (!hasCandidate) return null

        val hasVerb = DefaultKeywords.FUNDRAISING_VERBS.any { body.contains(it) }
        if (hasVerb) {
            val matchedName = candidateNames.first { body.contains(it) }
            return Verdict.Kill("CAMPAIGN_HEURISTIC", matchedName)
        }

        return null
    }

    fun isShortcode(sender: String): Boolean = SHORTCODE_PATTERN.matches(sender.trim())
}
