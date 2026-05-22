package com.personal.ptk.classify

interface ContactChecker {
    fun isKnown(sender: String): Boolean
}
