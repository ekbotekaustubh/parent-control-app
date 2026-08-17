package com.familyguard.child.di

import android.content.Context
import androidx.room.Room
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
            .build()

    @Provides
    fun provideCachedRuleDao(db: ChildDatabase): CachedRuleDao = db.cachedRuleDao()

    @Provides
    fun provideUsageLedgerDao(db: ChildDatabase): UsageLedgerDao = db.usageLedgerDao()

    @Provides
    fun provideSyncQueueDao(db: ChildDatabase): SyncQueueDao = db.syncQueueDao()
}
