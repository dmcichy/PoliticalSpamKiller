package com.personal.ptk.classify

object HeuristicRules {

    private val SHORTCODE_PATTERN = Regex("^\\d{5,6}$")

    fun check(sender: String, body: String): Verdict.Kill? {
        val normalized = KeywordRules.collapseAndLower(body)
        checkCampaignHeuristic(normalized)?.let { return it }
        return null
    }

    private fun checkCampaignHeuristic(body: String): Verdict.Kill? {
        val candidateNames = listOf(
            "trump", "biden", "harris", "vance", "walz", "desantis", "newsom",
            "paxton", "cruz", "abbott", "mcconnell", "pelosi", "schumer",
            "ocasio-cortez", "gaetz", "boebert"
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
