package com.familyguard.child.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedScheduleDao {

    @Query("SELECT * FROM cached_schedules")
    suspend fun getAll(): List<CachedScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(schedules: List<CachedScheduleEntity>)

    @Query("DELETE FROM cached_schedules")
    suspend fun clearAll()

    /** Full-snapshot replace, matching `/device/config`'s response shape — same pattern as [CachedRuleDao.replaceAll]. */
    suspend fun replaceAll(schedules: List<CachedScheduleEntity>) {
        clearAll()
        upsertAll(schedules)
    }
}
