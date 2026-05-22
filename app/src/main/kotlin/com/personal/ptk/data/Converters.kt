package com.personal.ptk.data

import androidx.room.TypeConverter
import com.personal.ptk.data.entities.RuleType

class Converters {
    @TypeConverter
    fun fromRuleType(value: RuleType): String = value.name

    @TypeConverter
    fun toRuleType(value: String): RuleType = RuleType.valueOf(value)
}
