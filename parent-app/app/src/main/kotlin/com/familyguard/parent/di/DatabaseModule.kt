package com.familyguard.parent.di

import android.content.Context
import androidx.room.Room
import com.familyguard.parent.data.local.db.ChildCacheDao
import com.familyguard.parent.data.local.db.DashboardCacheDao
import com.familyguard.parent.data.local.db.ParentDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Small local cache only — children list + last-known dashboard snapshot per child, for
 * instant paint and pull-to-refresh. The backend remains the sole source of truth
 * (docs/architecture.md); nothing here is ever written except as a mirror of a successful
 * network response.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideParentDatabase(@ApplicationContext context: Context): ParentDatabase =
        Room.databaseBuilder(context, ParentDatabase::class.java, "parent_cache.db")
            // Cache-only data — safe to rebuild from the backend on schema changes instead
            // of writing/maintaining migrations for a non-authoritative local mirror.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideChildCacheDao(database: ParentDatabase): ChildCacheDao = database.childCacheDao()

    @Provides
    @Singleton
    fun provideDashboardCacheDao(database: ParentDatabase): DashboardCacheDao = database.dashboardCacheDao()
}
