# AI Agent 编程模块 - 扩展路线图

## 🎯 下一步实现优先级

### 优先级 1：核心功能完善（1-2 周）

#### 1.1 AI Provider 集成 ✅
**已完成任务**：
- [x] 创建 `AIProvider` 接口
- [x] 实现 `OpenAIAdapter`（使用 Retrofit）
- [x] 实现 `AnthropicAdapter`
- [x] 集成到 `StandardAgentWorkflow` 和依赖注入模块
- [x] 处理基本的 API Key 和模型配置

#### 1.2 工具调用解析 ✅
**已完成任务**：
- [x] 定义 AI 响应格式和模型
- [x] 实现工具调用解析
- [x] 工具执行和错误处理
- [x] 响应合并逻辑

#### 1.3 流式响应支持 ✅
**已完成任务**：
- [x] 添加 `executeStream` 方法到 Workflow 和 Provider 接口
- [x] 在 ViewModel 中处理流式响应
- [x] 在 UI 中实时显示 AI 输出 (新增 Streaming 状态)

---

### 优先级 2：代码分析工具（2-3 周）

#### 2.1 代码解析工具 ✅
```kotlin
// 创建 code 工具包
class ParseCodeTool @Inject constructor() : AgentTool() {
    override val name = "parse_code"
    override val description = "解析代码的 AST 结构"
    
    override suspend fun execute(args: Map<String, Any>): ToolResult {
        // 使用 Kotlin Compiler API 或其他语言的解析器
    }
}
```

**任务**：
- [x] 基于正则简单实现 AST 和语义信息提取的初级版本 (替代直接引入复杂编译器)
- [x] 返回结构化数据供 AI 使用
- [x] 在 ToolRegistry 中进行注册

#### 2.2 Lint 检查工具 ✅
```kotlin
class LintCheckTool @Inject constructor(
    private val containerEngine: LinuxContainerEngine
) : AgentTool() {
    override val name = "lint_check"
    override val description = "对代码进行静态检查"

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        // 在 Linux 容器内按文件类型选择 ktlint/eslint/flake8 等执行
    }
}
```

**任务**：
- [x] 通过 LinuxContainerEngine 在容器内执行 linter（替代直接引入 Detekt 依赖）
- [x] 按文件扩展名自动选择检查器 (ktlint/eslint/flake8/checkstyle 等)
- [x] 支持通过 linter 参数自定义检查命令
- [x] 在 ToolRegistry 与 AgentModule 中注册

#### 2.3 格式化工具 ✅
```kotlin
class FormatCodeTool @Inject constructor(
    private val containerEngine: LinuxContainerEngine
) : AgentTool() {
    override val name = "format_code"

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        // 在 Linux 容器内按文件类型选择格式化器并原地修改
    }
}
```

**任务**：
- [x] Kotlin 格式化（ktlint -F）
- [x] Java 格式化（google-java-format）
- [x] JavaScript/TypeScript 等格式化（Prettier）、Python（black）、Go、Rust
- [x] 支持通过 formatter 参数自定义格式化命令

---

### 优先级 3：数据持久化（2-3 周）

#### 3.1 Room 数据库 ✅
```kotlin
// 创建 database 层
@Entity(tableName = "agent_messages")
data class AgentMessageEntity(...)

@Dao
interface AgentMessageDao {...}

@Database(entities = [AgentMessageEntity::class], version = 1)
abstract class AgentDatabase : RoomDatabase() {...}
```

**任务**：
- [x] 创建 Message 实体和 DAO
- [x] 初始化 Database 和 Hilt 注入
- [x] (CodeChange 的长期留存暂时不需要，因为会在文件层直接应用或丢弃)

#### 3.2 消息历史管理 ✅
```kotlin
// 在 ViewModel 中加载历史消息
```

**任务**：
- [x] ViewModel 启动时加载所有的消息记录到 UI 中
- [x] 成功调用 / 发送信息时实时将 Message 持久化
- [x] 提供清空本地对话历史记录的操作

---

### 优先级 4：高级功能（3-4 周）

#### 4.1 多提供商支持 ✅
**任务**：
- [x] Provider 数据库和 DAO
- [x] Provider 管理 UI 面板
- [x] API Key 加密存储（Room 持久化配置，需进一步结合 Keystore）
- [x] 自定义 API 请求地址 (已实现 BaseUrl 自由切换)

#### 4.2 编辑器集成
**任务**：
- [ ] CodeMirror 6 WebView 集成
- [ ] 代码高亮和补全
- [ ] 编辑器和 Agent 同步

#### 4.3 代码差异视图
**任务**：
- [ ] 实现 Diff 算法
- [ ] Diff UI 展示 (目前基于 List 进行逐个 File 修改行展示)
- [ ] 逐行应用变更功能增强

---

### 优先级 5：高端特性（持续）

#### 5.1 AI 聊天面板
- [ ] 多轮对话记录显示 ✅ (已支持)
- [ ] Markdown 渲染
- [ ] 代码块高亮
- [ ] 消息搜索

#### 5.2 自动化工具
- [ ] 自动生成代码注释
- [ ] 自动生成测试用例
- [ ] 自动文档生成
- [ ] 性能优化建议

#### 5.3 项目管理
- [ ] 项目导入/导出
- [ ] 多项目支持
- [ ] 项目配置保存
- [ ] 工作空间管理

---

## 🔗 相关文档

- 技术设计文档: `docs/技术设计文档.md`
- UI 设计文档: `docs/UI设计文档.md`
- 实现总结: `IMPLEMENTATION_SUMMARY.md`

## 📊 工作量估计

| 优先级 | 功能 | 工作量 | 难度 |
|--------|------|--------|------|
| 1.1 | AI Provider 集成 | 已完成 | 中 |
| 1.2 | 工具调用解析 | 已完成 | 中 |
| 1.3 | 流式响应 | 已完成 | 中 |
| 2.1 | 代码解析 | 已完成 | 高 |
| 2.2 | Lint 检查 | 已完成 | 中 |
| 2.3 | 代码格式化 | 已完成 | 低 |
| 3.1 | Room 数据库 | 已完成 | 低 |
| 3.2 | 消息历史 | 已完成 | 低 |
| 4.1 | 多提供商动态支持 | 已完成 | 中 |
| 4.2 | 编辑器集成 | 4-5 天 | 高 |
| 4.3 | Diff 视图增强 | 3-4 天 | 高 |

**总估计**: 10-15 天（含测试）

---

**下一步行动**: 优先级 2（代码分析工具）已全部完成 —— ParseCodeTool、LintCheckTool、FormatCodeTool 均已接入容器执行并注册。接下来将重心转移至 4.2 代码编辑器集成 (接入 CodeMirror 6，实现代码高亮与交互)，或 4.3 Diff 视图增强！