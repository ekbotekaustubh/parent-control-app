package com.familyguard.parent.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChildCacheEntity::class, DashboardCacheEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ParentDatabase : RoomDatabase() {
    abstract fun childCacheDao(): ChildCacheDao
    abstract fun dashboardCacheDao(): DashboardCacheDao
}
