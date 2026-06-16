# 快速开始指南

## 📁 项目结构

```
app/
├── build.gradle.kts                           ← 依赖配置
├── src/main/
│   ├── AndroidManifest.xml                    ← 应用清单
│   ├── java/com/aicodeeditor/
│   │   ├── AIEditorApp.kt                     ← Hilt App
│   │   ├── MainActivity.kt                    ← 主入口
│   │   ├── core/theme/
│   │   │   └── AIEditorTheme.kt               ← Material 3 主题
│   │   ├── di/
│   │   │   └── AgentModule.kt                 ← DI 配置 (7 个工具注册)
│   │   └── feature/agent/
│   │       ├── data/
│   │       │   └── CodeChangeTracker.kt       ← 变更追踪
│   │       ├── domain/
│   │       │   ├── model/
│   │       │   │   ├── AgentMessage.kt        ← 消息模型
│   │       │   │   └── CodeChange.kt          ← 变更模型
│   │       │   ├── tool/
│   │       │   │   ├── AgentTool.kt           ← 工具基类
│   │       │   │   ├── ToolRegistry.kt        ← 工具注册中心
│   │       │   │   ├── file/
│   │       │   │   │   └── FileTools.kt       ← 4 个文件工具
│   │       │   │   └── editor/
│   │       │   │       └── EditorTools.kt     ← 3 个编辑器工具
│   │       │   └── workflow/
│   │       │       ├── AgentWorkflow.kt       ← 工作流接口
│   │       │       └── StandardAgentWorkflow.kt ← 标准实现
│   │       └── presentation/
│   │           ├── AIAgentViewModel.kt        ← 状态管理
│   │           └── component/
│   │               └── AIChatPanel.kt         ← Chat UI (4 个组件)
│   └── res/values/
│       ├── strings.xml
│       └── styles.xml
├── docs/
│   ├── 技术设计文档.md                         ← 完整架构设计
│   └── UI设计文档.md                           ← UI 规范
├── IMPLEMENTATION_SUMMARY.md                  ← 实现总结
└── ROADMAP.md                                 ← 扩展路线图
```

## 🚀 开始使用

### 1. 项目导入
```bash
# Android Studio 打开此项目
# File → Open → app/build.gradle.kts
```

### 2. 首次构建
```bash
# 同步 Gradle
# Build → Make Project

# 或命令行
./gradlew build
```

### 3. 运行应用
```bash
# 连接 Android 设备或启动模拟器
# Run → Run 'app'

# 或命令行
./gradlew installDebug
adb shell am start -n com.aicodeeditor/.MainActivity
```

## 💡 代码示例

### 在 Activity 中使用 Agent

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIEditorTheme {
                val viewModel: AIAgentViewModel = hiltViewModel()
                AIChatPanel(
                    viewModel = viewModel,
                    projectRoot = filesDir.absolutePath
                )
            }
        }
    }
}
```

### 执行 Agent 请求

```kotlin
// 用户输入一个需求
val request = "帮我创建一个排序函数"
viewModel.executeAgentRequest(
    request = request,
    currentFile = "/path/to/sort.kt",
    projectRoot = "/root"
)

// 监听结果
val messages: StateFlow<List<AgentUIMessage>> = viewModel.messages
val changes: StateFlow<List<CodeChange>> = viewModel.changes

// 用户审查后应用变更
viewModel.applyChanges(changes.value)
```

### 创建自定义工具

```kotlin
// 1. 继承 AgentTool
class MyCustomTool @Inject constructor() : AgentTool() {
    override val name = "my_tool"
    override val description = "我的自定义工具"
    override val parameters = mapOf(
        "param1" to ToolParameter("param1", ParameterType.STRING, "参数1")
    )
    
    override suspend fun execute(args: Map<String, Any>): ToolResult {
        return try {
            val param = args["param1"] as String
            // 实现工具逻辑
            ToolResult.Success(mapOf("result" to "成功"))
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "错误")
        }
    }
}

// 2. 在 AgentModule 注册
@Module
@InstallIn(SingletonComponent::class)
object AgentModule {
    @Provides
    @Singleton
    fun provideToolRegistry(
        // ... 其他工具
        myCustomTool: MyCustomTool
    ): ToolRegistry {
        return ToolRegistry().apply {
            // ... 注册其他工具
            register("my_tool", myCustomTool)
        }
    }
}
```

## 📊 工具列表

### 容器与环境
| 工具名 | 功能 | 参数 |
|--------|------|------|
| `execute_command` | 在轻量Linux容器执行Shell命令 (支持 git/npm) | command |

### 文件操作工具
| 工具名 | 功能 | 参数 |
|--------|------|------|
| `read_file` | 读取文件 | path, start_line?, end_line? |
| `write_file` | 写入文件 | path, content |
| `list_files` | 列出文件 | path, recursive?, max_depth? |
| `create_file` | 创建文件 | path, content? |

### 编辑器操作工具
| 工具名 | 功能 | 参数 |
|--------|------|------|
| `insert_code` | 插入代码 | file_path, line, code |
| `replace_code` | 替换代码 | file_path, start_line, end_line, new_code |
| `delete_code` | 删除代码 | file_path, start_line, end_line |

## 🔧 配置说明

### build.gradle.kts 中的主要依赖

```kotlin
// Compose
implementation("androidx.compose:compose-bom:2024.06.00")
implementation("androidx.compose.material3:material3")

// Hilt DI
implementation("com.google.dagger:hilt-android:2.51")
kapt("com.google.dagger:hilt-compiler:2.51")

// 协程
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

// Room (未来数据库)
implementation("androidx.room:room-runtime:2.6.0")
```

## 📱 UI 组件

### AIChatPanel
主要组件，包含聊天界面、消息列表、代码预览。

**Props:**
- `viewModel: AIAgentViewModel` - 状态管理
- `currentFile: String?` - 当前编辑文件
- `selectedCode: String?` - 选中的代码
- `projectRoot: String` - 项目根目录

### AgentMessageItem
显示单条消息（User/Assistant/Tool）。

### ChangePreviewPanel
显示代码变更预览和应用/拒绝按钮。

### ChangeItem
显示单条代码变更。

## 🧪 测试

### 运行单元测试
```bash
./gradlew test
```

### 运行 UI 测试
```bash
./gradlew connectedAndroidTest
```

### 测试工具
```kotlin
@Test
fun testReadFileTool() = runTest {
    val tool = ReadFileTool()
    val result = tool.execute(mapOf("path" to "/test/file.txt"))
    assertTrue(result is ToolResult.Success)
}
```

## 🐛 调试技巧

### 查看日志
```bash
./gradlew assembleDebug
adb logcat
```

### 在 logcat 中过滤
```bash
adb logcat | grep "AIAgent"
```

### Android Studio 调试
1. 设置断点
2. Run → Debug 'app'
3. 单步调试

## 📚 相关文档

- **技术设计**: `docs/技术设计文档.md` - 完整的架构设计
- **UI 设计**: `docs/UI设计文档.md` - UI 规范和交互设计
- **实现总结**: `IMPLEMENTATION_SUMMARY.md` - 当前实现的代码统计
- **扩展路线**: `ROADMAP.md` - 未来功能规划

## ⚠️ 已知限制

当前版本是 MVP（最小化可行产品），以下功能待实现：

- ❌ 真实 AI Provider 集成（当前为桩实现）
- ❌ 工具调用的 AI 解析（需要 Provider API）
- ❌ 代码分析工具（Lint、Parse）
- ❌ 数据库持久化
- ❌ 代码编辑器 WebView
- ❌ 多提供商支持

## 🎯 下一步

1. **集成 OpenAI API** - 参考 `ROADMAP.md` 的优先级 1.1
2. **实现工具调用解析** - 优先级 1.2
3. **添加代码分析工具** - 优先级 2
4. **数据库和持久化** - 优先级 3

## 💬 反馈和支持

如有问题或建议，请参考：
- 技术设计文档中的架构说明
- ROADMAP 中的扩展计划
- 代码注释中的说明

---

**祝你使用愉快！** 🎉
