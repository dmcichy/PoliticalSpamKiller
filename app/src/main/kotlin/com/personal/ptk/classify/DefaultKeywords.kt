package com.personal.ptk.classify

object DefaultKeywords {

    val ALL: List<String> = listOf(
        // Voting / elections
        "political", "voter registration", "ballot measure", "election day",
        "campaign trail", "filibuster", "impeach",
        "reelect", "re-elect", "midterm election",
        "electoral college",

        // Donation solicitation
        "donate", "donation", "donor", "chip in", "chipping in", "pitch in",
        "match my donation", "match 5x", "matched 500%",
        "triple match", "quadruple match",
        "2x match", "5x match", "10x match",
        "your contribution",

        // Urgency / pressure tactics (political-specific only)
        "midnight deadline", "fec deadline",

        // Parties / movements / committees
        "gop", "dnc", "rnc", "maga", "democrat", "republican",
        "nrcc", "nrsc", "dccc", "dscc",
        "super pac", "political action",
        "red wave", "blue wave",
        "tea party", "freedom caucus",

        // Candidates / politicians (2024-2026 cycle)
        "trump", "biden", "harris", "vance", "walz", "desantis", "newsom",
        "paxton", "cruz", "abbott", "mcconnell", "pelosi", "schumer",
        "ocasio-cortez", "gaetz", "boebert",
        "marjorie taylor greene",

        // Fundraising platforms
        "actblue", "winred", "anedot", "donorbox",

        // Common political spam phrases (multi-word = safe)
        "are you with us", "stand with",
        "fight back", "take back", "save america",
        "join the fight",
        "signed the petition", "sign the petition"
    )

    val DONATION_INDICATORS: Set<String> = setOf(
        "$", "match", "donate", "give", "chip in",
        "gift", "contribution", "pitch in"
    )

    val FUNDRAISING_DOMAINS: List<String> = listOf(
        "actblue.com", "winred.com", "secure.anedot.com", "donorbox.org"
    )

    val FUNDRAISING_VERBS: Set<String> = setOf(
        "donate", "contribute", "chip in", "pitch in", "give",
        "support", "stand with", "back", "fund", "match"
    )

    val COMMON_STOPWORDS: Set<String> = setOf(
        "the", "a", "an", "is", "are", "was", "were", "be", "been",
        "being", "have", "has", "had", "do", "does", "did", "will",
        "would", "could", "should", "may", "might", "shall", "can",
        "to", "of", "in", "for", "on", "with", "at", "by", "from",
        "as", "into", "through", "during", "before", "after", "above",
        "below", "between", "out", "off", "over", "under", "again",
        "further", "then", "once", "here", "there", "when", "where",
        "why", "how", "all", "each", "every", "both", "few", "more",
        "most", "other", "some", "such", "no", "nor", "not", "only",
        "own", "same", "so", "than", "too", "very", "just", "because",
        "but", "and", "or", "if", "while", "about", "up", "this",
        "that", "these", "those", "i", "me", "my", "we", "our", "you",
        "your", "he", "him", "his", "she", "her", "it", "its", "they",
        "them", "their", "what", "which", "who", "whom"
    )
}
