package com.familyguard.child.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Holds only non-sensitive, device-local cache/ledger/queue data. Tokens and other
 * credentials MUST NOT be added here — Room's underlying SQLite file is unencrypted on
 * disk; see `data/local/TokenStore.kt` (EncryptedSharedPreferences) for those instead.
 */
@Database(
    entities = [CachedRuleEntity::class, UsageLedgerEntity::class, SyncQueueEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ChildDatabase : RoomDatabase() {
    abstract fun cachedRuleDao(): CachedRuleDao
    abstract fun usageLedgerDao(): UsageLedgerDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        const val DATABASE_NAME = "child_database"
    }
}
