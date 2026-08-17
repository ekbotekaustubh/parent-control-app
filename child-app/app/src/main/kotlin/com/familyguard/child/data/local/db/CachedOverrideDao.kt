package com.familyguard.child.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedOverrideDao {

    @Query("SELECT * FROM cached_overrides WHERE expiresAtMillis > :nowMillis")
    suspend fun getActive(nowMillis: Long): List<CachedOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(overrides: List<CachedOverrideEntity>)

    @Query("DELETE FROM cached_overrides")
    suspend fun clearAll()

    /** Full-snapshot replace, matching `/device/config`'s response shape — same pattern as [CachedRuleDao.replaceAll]. */
    suspend fun replaceAll(overrides: List<CachedOverrideEntity>) {
        clearAll()
        upsertAll(overrides)
    }
}
