package com.familyguard.parent.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildCacheDao {

    @Query("SELECT * FROM child_cache ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ChildCacheEntity>>

    @Upsert
    suspend fun upsertAll(children: List<ChildCacheEntity>)

    @Query("DELETE FROM child_cache")
    suspend fun clearAll()

    /** Used after a successful `GET /children` refresh so the cache never shows stale/removed rows. */
    @Transaction
    suspend fun replaceAll(children: List<ChildCacheEntity>) {
        clearAll()
        upsertAll(children)
    }
}
