---
title: "Opencode Architecture Analysis"
description: "Analysis of Agents, MCP, Skills, Prompt Injection, and Context Compression"
---

# Opencode 核心架构实现分析

我们在对 `opencode` 项目进行了源码分析后，整理了其在 Agent、MCP、Skill、提示词注入以及上下文压缩方面的实现原理。本项目高度依赖于 **Effect-TS**，采用函数式、分层（Layer）与上下文（Context）驱动的现代化架构。以下是各个核心模块的代码层实现分析：

## 1. Agents (智能体实现)

**相关代码路径:** 
- `packages/core/src/agent.ts`
- `packages/core/src/state.ts`

* **架构与定义：** Agent 的核心逻辑建立在 Effect 的服务层级上。底层定义了专门的 `Agent` Schema，包含了智能体的 ID、Info 元数据、属性（如 Color）等。
* **状态管理 (State & Draft)：** Agent 的生命周期和内存管理由 `State.create<Data, Draft>` 来维护。系统通过类似于不可变状态树的机制（Draft 模式）来增、删、改、查 Agent。
* **分发与选择：** 提供了一组基于 Effect 的 API（如 `get`、`list`、`default`、`select`、`resolve`），用于调度不同的 Agent 实体。特别是通过 `default` 可以实现当前环境下的兜底 Agent 选择。各个 Agent 内部的状态是相互隔离的。

## 2. MCP (Model Context Protocol 实现)

**相关代码路径:** 
- `packages/protocol`
- `packages/llm/src/tool.ts`
- `packages/core/src/plugin.ts`
- `packages/core/src/integration.ts`

* **动态工具注册：** MCP 协议的核心之一是动态上下文和工具发现。该项目在处理工具 schema 时（`GenerateObjectDynamicOptions`）被设计为允许在运行时接收和验证未知的、由外部传入的 `JsonSchema`。这直接迎合了 MCP 服务端下发动态 Tool 的特性。
* **插件化接入：** 借助内部的 Plugin 机制，实现了完整的插件生命周期管理（加载 `add`、卸载 `remove`、同步等待 `wait`）。通过精细的锁机制（`KeyedMutex`）和独立的作用域（`Scope.fork`），MCP Client 或外部扩展可以作为独立插件挂载进系统，与 Agent 进行双向通信而不会造成主进程阻塞。
* **鉴权与集成 (Integration)：** 包含了一套完整的 Integration 服务负责管理各类授权（如 OAuth、API Key），这也是连接受保护的外部 MCP Server 时的重要鉴权基座。

## 3. Skill (技能机制)

**相关代码路径:** 
- `packages/core/src/skill.ts`
- `packages/schema/skill`

* **技能来源 (Sources)：** Skill 被设计为高度可插拔的模块，支持从本地目录 (`DirectorySource`)、远程 URL (`UrlSource`) 或者内存内嵌 (`EmbeddedSource`) 动态加载。
* **基于 Markdown 的定义：** 技能并不是硬编码在代码中的。系统会扫描指定源下的 `{*.md,**/SKILL.md}` 文件，通过解析文件的 **Frontmatter (YAML 前置元数据)** 来提取 `name`、`description` 和 `slash` (是否支持斜杠命令) 等配置，并将 Markdown 的主体正文直接作为技能的 Prompt 指令内容加载。
* **鉴权拦截：** 在触发某项 Skill 之前，系统会调用 `PermissionV2.evaluate` 来校验当前 Agent 的权限配置，从而实现细粒度的安全控制。

## 4. 提示词注入 (System Prompt 管理)

**相关代码路径:** 
- `packages/core/src/system-context/index.ts`
- `packages/llm/src/llm.ts`

专门解决长对话中全量重新拼接 Prompt 导致的性能和缓存命中率问题：
* **独立刷新的上下文树 (SystemContext)：** 将不同类别的提示词（如系统核心 Prompt、文件树上下文、当前任务目标等）抽象为“独立可刷新的类型化数据源” (`Source<A>`)。
* **增量更新与快照：** 每个 Prompt Source 被构建时会生成一个持久化快照 (`Snapshot`) 和一个不可变的基线文本 (`baseline`)。当某个局部的上下文发生变化时，系统通过 `reconcile` 和 `compare` 函数仅对变动部分进行更新或替换，动态组装出 diff 文本。这种方式极大地优化了向 LLM 注入极大规模 Prompt 时的效率。
* **最终组装：** 所有的系统上下文、用户输入和历史消息最终会在发起请求时被格式化，合并打包发送给大语言模型。

## 5. 上下文压缩 (Context Compaction)

**相关代码路径:** 
- `packages/core/src/session/compaction.ts`

为了解决超长对话带来的 Token 溢出和“灾难性遗忘”问题，项目实现了一套 **“结构化摘要提取 + 最近上下文保留”** 的自动压缩机制：

* **动态触发 (Triggering)：** 在每次 LLM 请求前，系统会计算当前的 Token 消耗。若 `当前请求的总 Token > 模型最大上下文 - Math.max(设定输出上限, 20000缓冲)`，则触发压缩。
* **历史安全分割 (History Splitting)：** 并不是把所有消息都拿去压缩，而是从后往前切分。系统会保留最近的一定数量的 Token（默认 8,000）保持原样（`recent`），而只把这 8000 Token 之前的“古老历史”（`head`）送去压缩。长工具返回在进入压缩流前会被截断为最多 2,000 字符。
* **强制结构化摘要 (Structured Summarization)：** 使用极其严格的内置 Markdown 模板 (`SUMMARY_TEMPLATE`)，强制后台 LLM 提取并输出固定结构的总结：
  * **Goal (目标)**
  * **Constraints & Preferences (约束与偏好)**
  * **Progress (进度, 分为 Done/In Progress/Blocked)**
  * **Key Decisions (关键决策)**
  * **Next Steps (下一步)**
  * **Critical Context (关键上下文)**
  * **Relevant Files (相关文件)**
  * *注：若发生二次压缩，系统会将【旧摘要 + 新历史】一同发给 LLM 并要求更新，而非重新生成。*
* **状态更替：** 压缩完成后，历史节点会被合并为一条极度精简的底层系统消息，成功将动辄数万的无关对话 Token 坍缩为几千 Token，且不丢失当前任务细节。
