package com.familyguard.child.di

import android.content.Context
import androidx.room.Room
import com.familyguard.child.data.local.db.CachedOverrideDao
import com.familyguard.child.data.local.db.CachedRuleDao
import com.familyguard.child.data.local.db.ChildDatabase
import com.familyguard.child.data.local.db.SyncQueueDao
import com.familyguard.child.data.local.db.UsageLedgerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideChildDatabase(@ApplicationContext context: Context): ChildDatabase =
        Room.databaseBuilder(context, ChildDatabase::class.java, ChildDatabase.DATABASE_NAME)
            // See ChildDatabase's KDoc on the version 2 bump — no real device has this
            // database pre-version-2 yet, so destructive fallback is fine for now.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCachedRuleDao(db: ChildDatabase): CachedRuleDao = db.cachedRuleDao()

    @Provides
    fun provideUsageLedgerDao(db: ChildDatabase): UsageLedgerDao = db.usageLedgerDao()

    @Provides
    fun provideSyncQueueDao(db: ChildDatabase): SyncQueueDao = db.syncQueueDao()

    @Provides
    fun provideCachedOverrideDao(db: ChildDatabase): CachedOverrideDao = db.cachedOverrideDao()
}
