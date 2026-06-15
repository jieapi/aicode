package com.aicodeeditor.feature.agent.domain.workflow

import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.model.WorkflowResult
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import kotlinx.coroutines.flow.Flow

interface AgentWorkflow {
    suspend fun execute(
        userRequest: String,
        context: AgentContext,
        tools: List<AgentTool>
    ): WorkflowResult

    fun executeStream(
        userRequest: String,
        context: AgentContext,
        tools: List<AgentTool>
    ): Flow<String>
}
