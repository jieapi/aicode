package com.aicodeeditor.di

import com.aicodeeditor.feature.settings.domain.repository.AIProviderRepository
import com.aicodeeditor.feature.settings.data.repository.AIProviderRepositoryImpl
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
}
