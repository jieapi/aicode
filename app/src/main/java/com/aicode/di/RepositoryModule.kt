package com.aicode.di

import com.aicode.feature.settings.domain.repository.AIProviderRepository
import com.aicode.feature.settings.data.repository.AIProviderRepositoryImpl
import com.aicode.feature.credentials.domain.repository.CredentialRepository
import com.aicode.feature.credentials.data.repository.CredentialRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAIProviderRepository(
        aiProviderRepositoryImpl: AIProviderRepositoryImpl
    ): AIProviderRepository

    @Binds
    @Singleton
    abstract fun bindCredentialRepository(
        credentialRepositoryImpl: CredentialRepositoryImpl
    ): CredentialRepository
}
