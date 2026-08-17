package com.familyguard.parent.di

import com.familyguard.parent.data.repository.AccessRequestRepository
import com.familyguard.parent.data.repository.AccessRequestRepositoryImpl
import com.familyguard.parent.data.repository.AuthRepository
import com.familyguard.parent.data.repository.AuthRepositoryImpl
import com.familyguard.parent.data.repository.ChildRepository
import com.familyguard.parent.data.repository.ChildRepositoryImpl
import com.familyguard.parent.data.repository.DashboardRepository
import com.familyguard.parent.data.repository.DashboardRepositoryImpl
import com.familyguard.parent.data.repository.PairingRepository
import com.familyguard.parent.data.repository.PairingRepositoryImpl
import com.familyguard.parent.data.repository.RuleRepository
import com.familyguard.parent.data.repository.RuleRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds each repository interface to its real implementation. Interfaces (rather than
 * injecting concrete classes directly) exist specifically so ViewModel unit tests can
 * construct a fake implementation with no Hilt/Android dependency at all.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindChildRepository(impl: ChildRepositoryImpl): ChildRepository

    @Binds
    @Singleton
    abstract fun bindPairingRepository(impl: PairingRepositoryImpl): PairingRepository

    @Binds
    @Singleton
    abstract fun bindRuleRepository(impl: RuleRepositoryImpl): RuleRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(impl: DashboardRepositoryImpl): DashboardRepository

    @Binds
    @Singleton
    abstract fun bindAccessRequestRepository(impl: AccessRequestRepositoryImpl): AccessRequestRepository
}
