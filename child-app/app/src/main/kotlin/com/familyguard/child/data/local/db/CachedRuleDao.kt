package com.familyguard.child.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedRuleDao {

    @Query("SELECT * FROM cached_rules")
    fun observeAll(): Flow<List<CachedRuleEntity>>

    @Query("SELECT * FROM cached_rules")
    suspend fun getAll(): List<CachedRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<CachedRuleEntity>)

    @Query("DELETE FROM cached_rules")
    suspend fun clearAll()

    @Delete
    suspend fun delete(rule: CachedRuleEntity)

    /**
     * Replaces the entire cached rule set atomically (clear + insert), matching
     * `/device/config`'s full-snapshot response shape (it always returns the complete
     * current rule list for the child, not a delta).
     */
    suspend fun replaceAll(rules: List<CachedRuleEntity>) {
        clearAll()
        upsertAll(rules)
    }
}
