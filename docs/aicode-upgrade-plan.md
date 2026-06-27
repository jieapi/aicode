---
title: "aicode 项目架构升级与优化计划"
description: "基于 opencode 先进架构思想的跨代升级方案，分多个阶段逐步演进。"
---

# aicode 项目架构升级与演进指南

基于业界前沿的 Agent 架构思想（参考 `opencode`），我们为 `aicode` 制定了分阶段的底层升级计划。该计划旨在彻底解决长会话 Token 溢出问题，提升大模型的响应效率，并使整个 Agent 工作流更加健壮、可重放。

整体升级将分为以下阶段逐步进行，确保现有核心功能的稳定性。同时，我们也明确了当前项目中具备业界领先优势、应当予以保留和发扬的模块。

---

## 阶段一：基础设施增强与生态拓展 (基础优化)
**目标：** 解决当前解析层较弱的问题，拓展 Skill 的数据源边界。
**预计成本：** 低

### 1. 升级 Skill 解析引擎 (`SkillRepository.kt`)
- **废弃硬编码解析：** 移除手动字符串查找（`splitFrontmatter`），引入成熟的 YAML 解析库（如 `snakeyaml`）来安全、可靠地提取 Frontmatter 元数据。
- **拓展 Frontmatter 定义：** 支持在 Skill 元数据中声明 `required_tools`。在加载具体 Skill 时，按需为 LLM 注入外部 Tool Schema，降低基础请求的 Payload 负担。

### 2. 引入多元化技能数据源 (Skill Sources)
- **抽象 Source 接口：** 定义 `SkillSource` 接口，保留现有的 `LocalDirectorySkillSource`（基于本地 `filesDir`）。
- **增加远程支持：** 实现 `UrlSkillSource`，允许用户配置外部 GitHub Raw 链接或配置中心链接，启动时动态同步团队统一的 Prompt / Skill 模板。

---

## 阶段1.5：工具体系、权限调优与 AI 自治增强 (扬长补短)
**目标：** 保持当前项目在权限安全分析上的绝对领先优势，补齐在工具生命周期与资源限制上的不足，并提升 AI 自动配置环境的能力。
**预计成本：** 中

### 1. 保持并发挥硬核的权限防御体系（千万不要乱改）
- **现状认可：** 当前 `aicode` 的 `ShellCommandParser.kt` 和 `ToolPermissionPolicyEngine.kt` 极其优秀。其基于 AST 级别的词法分析（识别嵌套引号、拦截绝对路径重定向、降级 `$(...)` 命令替换）以及子命令级别的精细记忆（如区分 `git pull` 与 `git push`）在行业内非常先进。事实上，`opencode` 团队甚至在源码 TODO 中计划开发类似你现有的这套解析器。
- **演进方向：** 保持该引擎的独立性。未来可考虑将基于 SQLite 或本地记忆的 `PermissionRule` 抽象为可云端同步的配置文件，实现团队级的安全规则下发。

### 2. 补齐工具沙箱限制
- **强制超时机制 (Timeouts)：** 为 `AgentTool` 接口增加强制超时管理。设定默认超时（如 120 秒）和最大超时上限，避免 Shell 命令死循环或长时间阻塞导致 Agent 失联。
- **内存防爆截断 (Memory Bounds)：** 对于非流式的大块输出，在工具层（如 `ExecuteCommandTool`）增加最高内存读取限制（例如最大捕获 `1MB` 输出），超过部分主动截断并附加警告，防止大体积日志直接撑爆主存或 LLM 上下文。

### 3. 提升 AI 自治配置能力 (参考 opencode 的生态引导)
- **现状诊断：** 当前项目的 `70-skills-and-mcp.md` 提示词非常简陋且被动，仅仅是告诉 AI：“配置在 `/root/.aicode/mcp.json`，你可以自己用文本编辑工具去改”。这导致 AI 经常改错 JSON 格式，且不懂得自动安装环境（如 nodejs/python）。
- **优化方案：** 
  - **提供专属管理工具 (Management Tools)：** 废弃让 AI 手改配置文件的做法。提供专门的 `mcp_install`、`skill_add` 等抽象工具。
  - **自动化依赖处理：** 当 AI 调用 `mcp_install` 时，底层宿主应用自动帮它安装对应的 `node`/`npm`/`python` 环境并校验 JSON 语法，而不是指望大模型自己去跑 `apk add`。
  - **生态市场引导：** 在 System Prompt 中注入“生态市场”的概念。当用户需要数据库连接或 GitHub 搜索时，引导 AI 主动调用 `mcp_search` 去内置配置库中查找合适的 MCP Server 并一键安装。

---

## 阶段二：LLM 性能与上下文深度优化 (核心痛点解决)
**目标：** 彻底解决长对话导致的“上下文溢出”崩溃，提升高频请求的 KV Cache 命中率。
**预计成本：** 高

### 1. 实现增量式提示词更新 (SystemContext 化)
- **重构 `SystemPromptProvider.kt`：**
  - **切分源数据 (Source)：** 把当前的拼装逻辑拆分为独立的 `Source` 模块，如 `StaticRuleSource`、`WorkspaceSource`、`ActiveSkillsSource`。
  - **基线与快照 (Baseline & Snapshot)：** 为每个 Source 维护上一次生成的快照。
  - **增量 Diff：** 当请求发起时，对比本次与上次的快照，若仅有 `WorkspaceSource`（如当前打开的文件）发生变化，则维持基线不变，仅向大模型注入“变化部分”的指令。以此大幅降低重复 Token 处理。

### 2. 引入智能上下文压缩机制 (Context Compaction)
- **动态 Token 监控：** 在 `StandardAgentWorkflow` 中，集成轻量级 Token 估算机制。每次请求前预估 `history` 的占用容量。
- **触发与切片：** 当预估 Token 超过 `模型最大窗口 - 预留缓冲区` 时，触发压缩机制。将历史 `messages` 分为 `Head`（老数据）和 `Recent`（最近的 ~8000 Token）。
- **结构化坍缩 (Summarization)：** 
  - 启动一个后台 LLM 任务，携带严格的 Markdown 模板对 `Head` 数据进行强制结构化摘要。
  - 任务完成后，将数据库中的老数据合并替换为一条极度精简的 `SystemMessage`。这不仅避免了 `max_tokens` 溢出崩溃，还赋予了模型超长期的“无损记忆”。

---

## 阶段三：核心工作流与状态管理重构 (架构升维)
**目标：** 告别指令式的大循环，走向真正的不可变状态流（Immutable State Flow），实现完美容错与可测试性。
**预计成本：** 中高

### 1. 消除大循环与 MutableList (`StandardAgentWorkflow.kt`)
- **状态树重构：** 废弃现存的 `while (iterations < MAX_ITERATIONS)` 与可变的 `messages` 列表。
- **MVI / StateFlow 架构引入：** 定义一个完整的不可变状态树（`AgentSessionState`）。每一次 LLM 返回、每一次 Tool Call，都视为一个 `Action`，并通过 `Reducer` 生成一棵全新的状态树。

### 2. 纯函数副作用抽离 (Side-Effect Isolation)
- **解耦业务逻辑：** 将涉及权限弹窗、执行 Shell 脚本等涉及环境依赖的操作，彻底从工作流主骨架中抽离，定义为 `SideEffect`。
- **重放与回滚支持：** 借助于不可变状态树，当某个 Tool 发生崩溃，或用户主动取消/拒绝授权时，系统只需将状态指针回退到上一版（Revert），不仅免除了脏数据污染，也为以后开发“时间旅行（Time-travel Debugging）”级别的撤销功能打下基础。
