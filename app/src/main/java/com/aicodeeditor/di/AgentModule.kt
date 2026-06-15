package com.aicodeeditor.di

import android.content.Context
import androidx.room.Room
import com.aicodeeditor.feature.agent.data.local.dao.AgentMessageDao
import com.aicodeeditor.feature.settings.domain.repository.AIProviderRepository
import com.aicodeeditor.feature.settings.data.repository.AIProviderRepositoryImpl
import com.aicodeeditor.feature.agent.data.local.database.AgentDatabase
import com.aicodeeditor.feature.agent.data.CodeChangeTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicApi
import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIApi
import com.aicodeeditor.feature.agent.domain.provider.AIProvider
import com.aicodeeditor.feature.agent.domain.provider.AnthropicAdapter
import com.aicodeeditor.feature.agent.domain.provider.OpenAIAdapter
import com.aicodeeditor.feature.agent.domain.tool.file.CreateFileTool
import com.aicodeeditor.feature.agent.domain.tool.file.ListFilesTool
import com.aicodeeditor.feature.agent.domain.tool.file.ReadFileTool
import com.aicodeeditor.feature.agent.domain.tool.file.WriteFileTool
import com.aicodeeditor.feature.agent.domain.tool.editor.DeleteCodeTool
import com.aicodeeditor.feature.agent.domain.tool.editor.InsertCodeTool
import com.aicodeeditor.feature.agent.domain.tool.editor.ReplaceCodeTool
import com.aicodeeditor.feature.agent.domain.tool.code.ParseCodeTool
import com.aicodeeditor.feature.agent.domain.workflow.AgentWorkflow
import com.aicodeeditor.feature.agent.domain.workflow.StandardAgentWorkflow
import com.aicodeeditor.feature.agent.domain.tool.ToolRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    @Provides
    @Singleton
    fun provideAgentDatabase(@ApplicationContext context: Context): AgentDatabase {
        return Room.databaseBuilder(
            context,
            AgentDatabase::class.java,
            "aicodeeditor_agent_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideAgentMessageDao(database: AgentDatabase): AgentMessageDao {
        return database.agentMessageDao()
    }

    @Provides
    @Singleton
    fun provideAIProviderDao(database: AgentDatabase): AIProviderDao {
        return database.aiProviderDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("OpenAI")
    fun provideOpenAIRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("Anthropic")
    fun provideAnthropicRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAIApi(@Named("OpenAI") retrofit: Retrofit): OpenAIApi {
        return retrofit.create(OpenAIApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAnthropicApi(@Named("Anthropic") retrofit: Retrofit): AnthropicApi {
        return retrofit.create(AnthropicApi::class.java)
    }

    @Provides
    @Singleton
    @Named("OpenAIProvider")
    fun provideOpenAIProvider(api: OpenAIApi): AIProvider {
        return OpenAIAdapter(api)
    }

    @Provides
    @Singleton
    @Named("AnthropicProvider")
    fun provideAnthropicProvider(api: AnthropicApi): AIProvider {
        return AnthropicAdapter(api)
    }

    @Provides
    @Singleton
    fun provideToolRegistry(
        readFileTool: ReadFileTool,
        writeFileTool: WriteFileTool,
        listFilesTool: ListFilesTool,
        createFileTool: CreateFileTool,
        insertCodeTool: InsertCodeTool,
        replaceCodeTool: ReplaceCodeTool,
        deleteCodeTool: DeleteCodeTool,
        parseCodeTool: ParseCodeTool
    ): ToolRegistry {
        return ToolRegistry().apply {
            register("read_file", readFileTool)
            register("write_file", writeFileTool)
            register("list_files", listFilesTool)
            register("create_file", createFileTool)
            register("insert_code", insertCodeTool)
            register("replace_code", replaceCodeTool)
            register("delete_code", deleteCodeTool)
            register("parse_code", parseCodeTool)
        }
    }

    @Provides
    @Singleton
    fun provideCodeChangeTracker(): CodeChangeTracker {
        return CodeChangeTracker()
    }

    @Provides
    @Singleton
    fun provideAgentWorkflow(
        toolRegistry: ToolRegistry,
        @Named("OpenAIProvider") aiProvider: AIProvider // Defaulting to OpenAI for now
    ): AgentWorkflow {
        return StandardAgentWorkflow(toolRegistry, aiProvider)
    }
}
