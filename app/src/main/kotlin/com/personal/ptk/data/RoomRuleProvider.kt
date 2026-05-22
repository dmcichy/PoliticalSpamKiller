package com.personal.ptk.data

import com.personal.ptk.classify.Classifier
import com.personal.ptk.classify.RuleProvider

class RoomRuleProvider(private val ruleDao: RuleDao) : RuleProvider {

    override suspend fun isAllowlisted(number: String): Boolean {
        val normalized = Classifier.normalizeNumber(number)
        return ruleDao.isNumberAllowlisted(normalized)
    }

    override suspend fun isBlocklisted(number: String): Boolean {
        val normalized = Classifier.normalizeNumber(number)
        return ruleDao.isNumberBlocklisted(normalized)
    }

    override suspend fun getActiveKeywords(): List<String> =
        ruleDao.getAllActiveKeywords()
}
