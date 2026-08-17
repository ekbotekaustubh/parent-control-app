package com.familyguard.child.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Holds only non-sensitive, device-local cache/ledger/queue data. Tokens and other
 * credentials MUST NOT be added here — Room's underlying SQLite file is unencrypted on
 * disk; see `data/local/TokenStore.kt` (EncryptedSharedPreferences) for those instead.
 *
 * version 2 added [CachedOverrideEntity]. No `Migration` is defined for that bump —
 * `di/DatabaseModule.kt` uses `fallbackToDestructiveMigration()` instead, an acceptable
 * simplification pre-release (nothing has shipped to a real device yet, so there's no
 * user data to preserve across the bump).
 */
@Database(
    entities = [CachedRuleEntity::class, UsageLedgerEntity::class, SyncQueueEntity::class, CachedOverrideEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ChildDatabase : RoomDatabase() {
    abstract fun cachedRuleDao(): CachedRuleDao
    abstract fun usageLedgerDao(): UsageLedgerDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun cachedOverrideDao(): CachedOverrideDao

    companion object {
        const val DATABASE_NAME = "child_database"
    }
}
