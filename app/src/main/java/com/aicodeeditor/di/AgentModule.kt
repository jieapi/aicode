package com.aicodeeditor.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aicodeeditor.feature.agent.data.local.dao.AgentMessageDao
import com.aicodeeditor.feature.agent.data.local.dao.ChatSessionDao
import com.aicodeeditor.feature.agent.data.local.dao.TodoItemDao
import com.aicodeeditor.feature.settings.data.local.dao.AIProviderDao
import com.aicodeeditor.feature.settings.domain.model.ProviderType
import com.aicodeeditor.feature.settings.domain.repository.AIProviderRepository
import com.aicodeeditor.feature.agent.data.local.database.AgentDatabase
import com.aicodeeditor.feature.agent.data.CodeChangeTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicApi
import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIApi
import com.aicodeeditor.feature.agent.domain.provider.AIProvider
import com.aicodeeditor.feature.agent.domain.provider.AnthropicAdapter
import com.aicodeeditor.feature.agent.domain.provider.OpenAIAdapter
import com.aicodeeditor.feature.agent.domain.tool.file.ReadFileTool
import com.aicodeeditor.feature.agent.domain.tool.file.WriteFileTool
import com.aicodeeditor.feature.agent.domain.tool.editor.EditFileTool
import com.aicodeeditor.feature.agent.domain.tool.container.ExecuteCommandTool
import com.aicodeeditor.feature.agent.domain.tool.container.TerminalSessionTool
import com.aicodeeditor.feature.agent.domain.tool.skill.LoadSkillTool
import com.aicodeeditor.feature.agent.domain.tool.question.AskUserQuestionTool
import com.aicodeeditor.feature.agent.domain.tool.todo.TodoTool
import com.aicodeeditor.feature.agent.domain.prompt.SystemPromptProvider
import com.aicodeeditor.feature.agent.domain.workflow.AgentWorkflow
import com.aicodeeditor.feature.agent.domain.tool.ToolPermissionManager
import com.aicodeeditor.feature.agent.domain.permission.ToolPermissionPolicyEngine
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

import com.aicodeeditor.core.db.MigrationLoader

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
        ).addMigrations(*MigrationLoader.loadMigrations(context))
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()
    }

    @Provides
    @Singleton
    fun provideAgentMessageDao(database: AgentDatabase): AgentMessageDao {
        return database.agentMessageDao()
    }

    @Provides
    @Singleton
    fun provideChatSessionDao(database: AgentDatabase): ChatSessionDao {
        return database.chatSessionDao()
    }

    @Provides
    @Singleton
    fun provideAIProviderDao(database: AgentDatabase): AIProviderDao {
        return database.aiProviderDao()
    }

    @Provides
    @Singleton
    fun provideRemoteConnectionDao(database: AgentDatabase): com.aicodeeditor.feature.workspace.data.local.dao.RemoteConnectionDao {
        return database.remoteConnectionDao()
    }

    @Provides
    @Singleton
    fun provideTodoItemDao(database: AgentDatabase): TodoItemDao {
        return database.todoItemDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // 流式 SSE 下读超时是「相邻数据块之间」的等待上限；120s 给慢启动/长思考留足空间，
        // 真正卡死由上层阶梯重试（RetryPolicy）兜底。
        return OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
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
    @Named("Gemini")
    fun provideGeminiRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideGeminiApi(@Named("Gemini") retrofit: Retrofit): com.aicodeeditor.feature.agent.data.remote.gemini.GeminiApi {
        return retrofit.create(com.aicodeeditor.feature.agent.data.remote.gemini.GeminiApi::class.java)
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
    @Named("GeminiProvider")
    fun provideGeminiProvider(api: com.aicodeeditor.feature.agent.data.remote.gemini.GeminiApi): AIProvider {
        return com.aicodeeditor.feature.agent.domain.provider.GeminiAdapter(api)
    }

    @Provides
    @Singleton
    fun provideToolRegistry(
        readFileTool: ReadFileTool,
        writeFileTool: WriteFileTool,
        editFileTool: EditFileTool,
        executeCommandTool: ExecuteCommandTool,
        terminalSessionTool: TerminalSessionTool,
        loadSkillTool: LoadSkillTool,
        askUserQuestionTool: AskUserQuestionTool,
        manageMcpTool: com.aicodeeditor.feature.agent.domain.tool.mcp.ManageMcpTool,
        manageSkillTool: com.aicodeeditor.feature.agent.domain.tool.skill.ManageSkillTool,
        webSearchTool: com.aicodeeditor.feature.agent.domain.tool.search.WebSearchTool,
        webFetchTool: com.aicodeeditor.feature.agent.domain.tool.search.WebFetchTool,
        switchModeTool: com.aicodeeditor.feature.agent.domain.tool.mode.SwitchModeTool,
        todoTool: TodoTool
    ): ToolRegistry {
        return ToolRegistry().apply {
            register("readFile", readFileTool)
            register("writeFile", writeFileTool)
            register("editFile", editFileTool)
            register("Bash", executeCommandTool)
            register("terminal", terminalSessionTool)
            register("loadSkill", loadSkillTool)
            register("askUserQuestion", askUserQuestionTool)
            register("manageMcp", manageMcpTool)
            register("manageSkill", manageSkillTool)
            register("websearch", webSearchTool)
            register("webfetch", webFetchTool)
            register("switchMode", switchModeTool)
            register("todo", todoTool)
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
        aiProviderRepository: AIProviderRepository,
        @Named("OpenAIProvider") openAIProvider: AIProvider,
        @Named("AnthropicProvider") anthropicProvider: AIProvider,
        @Named("GeminiProvider") geminiProvider: AIProvider,
        promptProvider: SystemPromptProvider,
        permissionManager: ToolPermissionManager,
        policyEngine: ToolPermissionPolicyEngine,
        contextCompactor: com.aicodeeditor.feature.agent.domain.workflow.ContextCompactor
    ): AgentWorkflow {
        return com.aicodeeditor.feature.agent.domain.workflow.StatefulAgentWorkflow(
            toolRegistry,
            aiProviderRepository,
            openAIProvider,
            anthropicProvider,
            geminiProvider,
            promptProvider,
            permissionManager,
            policyEngine,
            contextCompactor
        )
    }
}
