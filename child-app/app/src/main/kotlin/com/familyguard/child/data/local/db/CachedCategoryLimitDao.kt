package com.familyguard.child.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedCategoryLimitDao {

    @Query("SELECT * FROM cached_category_limits")
    suspend fun getAll(): List<CachedCategoryLimitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(limits: List<CachedCategoryLimitEntity>)

    @Query("DELETE FROM cached_category_limits")
    suspend fun clearAll()

    /** Full-snapshot replace, matching `/device/config`'s response shape — same pattern as [CachedRuleDao.replaceAll]. */
    suspend fun replaceAll(limits: List<CachedCategoryLimitEntity>) {
        clearAll()
        upsertAll(limits)
    }
}
