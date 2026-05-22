package com.personal.ptk.classify

interface RuleProvider {
    suspend fun isAllowlisted(number: String): Boolean
    suspend fun isBlocklisted(number: String): Boolean
    suspend fun getActiveKeywords(): List<String>
}
