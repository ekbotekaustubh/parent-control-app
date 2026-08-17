package com.familyguard.child.di

import com.familyguard.child.domain.EnforcementDecider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `PairingRepository`, `RuleRepository`, and `UsageRepository` are all constructor-injected
 * directly (`@Inject constructor`, `@Singleton`) and need no explicit bindings here — Hilt
 * wires them automatically. This module exists for the one class that deliberately has no
 * framework annotations at all: `EnforcementDecider` is kept 100% dependency-free (see its
 * KDoc) so it stays trivially constructible in plain JUnit tests without pulling in Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideEnforcementDecider(): EnforcementDecider = EnforcementDecider()
}
