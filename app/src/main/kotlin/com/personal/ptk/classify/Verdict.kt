package com.personal.ptk.classify

sealed class Verdict {
    data object Allow : Verdict()
    data class Kill(val reason: String, val matchedRule: String) : Verdict()
}
