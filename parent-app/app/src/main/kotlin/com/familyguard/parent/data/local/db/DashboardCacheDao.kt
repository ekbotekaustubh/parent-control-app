package com.familyguard.parent.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DashboardCacheDao {

    @Query("SELECT * FROM dashboard_cache WHERE childId = :childId")
    fun observe(childId: String): Flow<DashboardCacheEntity?>

    @Query("SELECT * FROM dashboard_cache WHERE childId = :childId")
    suspend fun get(childId: String): DashboardCacheEntity?

    @Query("SELECT * FROM dashboard_cache")
    suspend fun getAll(): List<DashboardCacheEntity>

    @Upsert
    suspend fun upsert(entity: DashboardCacheEntity)
}
