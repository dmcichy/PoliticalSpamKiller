package com.personal.ptk.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.ptk.classify.DefaultKeywords
import com.personal.ptk.data.entities.RuleEntry
import com.personal.ptk.data.entities.RuleType
import com.personal.ptk.data.entities.VaultEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [VaultEntry::class, RuleEntry::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PtkDatabase : RoomDatabase() {

    abstract fun vaultDao(): VaultDao
    abstract fun ruleDao(): RuleDao

    companion object {
        @Volatile
        private var INSTANCE: PtkDatabase? = null

        fun getInstance(context: Context): PtkDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    DELETE FROM vault_entries WHERE id NOT IN (
                        SELECT MIN(id) FROM vault_entries GROUP BY sender, body, timestamp
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS
                        index_vault_entries_sender_body_timestamp
                        ON vault_entries (sender, body, timestamp)
                """.trimIndent())
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vault_entries ADD COLUMN smsId INTEGER")
            }
        }

        private fun buildDatabase(context: Context): PtkDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                PtkDatabase::class.java,
                "ptk.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(SeedCallback())
                .build()
    }

    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val rules = DefaultKeywords.ALL.map { keyword ->
                        RuleEntry(type = RuleType.KEYWORD, value = keyword)
                    }
                    database.ruleDao().insertAll(rules)
                }
            }
        }
    }
}
