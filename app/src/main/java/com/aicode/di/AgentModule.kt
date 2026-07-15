package com.aicode.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aicode.feature.agent.data.local.dao.AgentMessageDao
import com.aicode.feature.agent.data.local.dao.ChatSessionDao
import com.aicode.feature.agent.data.local.dao.TodoItemDao
import com.aicode.feature.settings.data.local.dao.AIProviderDao
import com.aicode.feature.settings.domain.model.ProviderType
import com.aicode.feature.settings.domain.repository.AIProviderRepository
import com.aicode.feature.agent.data.local.database.AgentDatabase
import com.aicode.feature.agent.data.CodeChangeTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import com.aicode.feature.agent.data.remote.anthropic.AnthropicApi
import com.aicode.feature.agent.data.remote.openai.OpenAIApi
import com.aicode.feature.agent.domain.provider.AIProvider
import com.aicode.feature.agent.domain.provider.AnthropicAdapter
import com.aicode.feature.agent.domain.provider.OpenAIAdapter
import com.aicode.feature.agent.domain.tool.file.ReadFileTool
import com.aicode.feature.agent.domain.tool.file.ViewImageTool
import com.aicode.feature.agent.domain.tool.file.WriteFileTool
import com.aicode.feature.agent.domain.tool.editor.EditFileTool
import com.aicode.feature.agent.domain.tool.container.ExecuteCommandTool
import com.aicode.feature.agent.domain.tool.container.TerminalSessionTool
import com.aicode.feature.agent.domain.tool.explorer.ListFilesTool
import com.aicode.feature.agent.domain.tool.explorer.SearchCodeTool
import com.aicode.feature.agent.domain.tool.skill.LoadSkillTool
import com.aicode.feature.agent.domain.tool.question.AskUserQuestionTool
import com.aicode.feature.agent.domain.tool.todo.TodoTool
import com.aicode.feature.agent.domain.prompt.SystemPromptProvider
import com.aicode.feature.agent.domain.workflow.AgentWorkflow
import com.aicode.feature.agent.domain.tool.ToolPermissionManager
import com.aicode.feature.agent.domain.permission.ToolPermissionPolicyEngine
import com.aicode.feature.agent.domain.tool.ToolRegistry
import com.aicode.feature.agent.domain.tool.ToolOutputStore
import com.aicode.feature.settings.data.remote.ModelMetadataService
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

import com.aicode.core.db.MigrationLoader

@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    @Provides
    @Singleton
    fun provideAgentDatabase(@ApplicationContext context: Context): AgentDatabase {
        return Room.databaseBuilder(
            context,
            AgentDatabase::class.java,
            "aicode_agent_db"
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
    fun provideRemoteConnectionDao(database: AgentDatabase): com.aicode.feature.workspace.data.local.dao.RemoteConnectionDao {
        return database.remoteConnectionDao()
    }

    @Provides
    @Singleton
    fun provideTodoItemDao(database: AgentDatabase): TodoItemDao {
        return database.todoItemDao()
    }

    @Provides
    @Singleton
    fun provideGitCredentialDao(database: AgentDatabase): com.aicode.feature.credentials.data.local.dao.GitCredentialDao {
        return database.gitCredentialDao()
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
    fun provideGeminiApi(@Named("Gemini") retrofit: Retrofit): com.aicode.feature.agent.data.remote.gemini.GeminiApi {
        return retrofit.create(com.aicode.feature.agent.data.remote.gemini.GeminiApi::class.java)
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
    fun provideGeminiProvider(api: com.aicode.feature.agent.data.remote.gemini.GeminiApi): AIProvider {
        return com.aicode.feature.agent.domain.provider.GeminiAdapter(api)
    }

    @Provides
    @Singleton
    fun provideToolRegistry(
        readFileTool: ReadFileTool,
        viewImageTool: ViewImageTool,
        writeFileTool: WriteFileTool,
        editFileTool: EditFileTool,
        executeCommandTool: ExecuteCommandTool,
        terminalSessionTool: TerminalSessionTool,
        listFilesTool: ListFilesTool,
        searchCodeTool: SearchCodeTool,
        loadSkillTool: LoadSkillTool,
        askUserQuestionTool: AskUserQuestionTool,
        manageMcpTool: com.aicode.feature.agent.domain.tool.mcp.ManageMcpTool,
        webSearchTool: com.aicode.feature.agent.domain.tool.search.WebSearchTool,
        webFetchTool: com.aicode.feature.agent.domain.tool.search.WebFetchTool,
        switchModeTool: com.aicode.feature.agent.domain.tool.mode.SwitchModeTool,
        todoTool: TodoTool
    ): ToolRegistry {
        return ToolRegistry().apply {
            register("readFile", readFileTool)
            register("viewImage", viewImageTool)
            register("writeFile", writeFileTool)
            register("editFile", editFileTool)
            register("Bash", executeCommandTool)
            register("terminal", terminalSessionTool)
            register("list", listFilesTool)
            register("search", searchCodeTool)
            register("loadSkill", loadSkillTool)
            register("askUserQuestion", askUserQuestionTool)
            register("manageMcp", manageMcpTool)
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
        contextCompactor: com.aicode.feature.agent.domain.workflow.ContextCompactor,
        planApprovalManager: com.aicode.feature.agent.domain.tool.mode.PlanApprovalManager,
        toolOutputStore: ToolOutputStore,
        modelMetadataService: ModelMetadataService,
        visionModelSettingsRepository: com.aicode.feature.settings.data.repository.VisionModelSettingsRepository,
        sessionUseCase: com.aicode.feature.agent.domain.session.SessionUseCase
    ): AgentWorkflow {
        return com.aicode.feature.agent.domain.workflow.StatefulAgentWorkflow(
            toolRegistry,
            aiProviderRepository,
            openAIProvider,
            anthropicProvider,
            geminiProvider,
            promptProvider,
            permissionManager,
            policyEngine,
            contextCompactor,
            planApprovalManager,
            toolOutputStore,
            modelMetadataService,
            visionModelSettingsRepository,
            sessionUseCase
        )
    }
}
