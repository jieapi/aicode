# 工具输出回填与落盘重构计划

## 背景

当前工具结果会直接作为 `ToolResult` 回填给 AI。`Bash` 已经通过 `BoundedOutput` 做了头尾限幅，但 `terminal`、`webfetch`、MCP 工具以及未来新增工具仍可能返回很长文本。尤其是构建日志、安装日志、网页正文、搜索结果或后台终端 transcript，如果完整塞进下一轮模型上下文，会快速膨胀上下文、增加成本，并可能触发上下文溢出。

参考 opencode 的做法，更合理的方案是把工具输出拆成三类：

- UI 实时输出：用于用户观看，不等同于模型上下文。
- 完整原始输出：超长时落盘保存，可追溯。
- AI 回填结果：只返回高信息密度 preview，加上完整输出路径和截断元数据。

## 目标

建立一个所有工具通用的输出处理层，而不是在每个工具里单独截断。

目标行为：

- 短输出原样回填给 AI。
- 长输出写入 `/root/.aicode/tool-output/`。
- AI 只收到 preview、`output_truncated`、`output_total_chars`、`output_path` 等元数据。
- 需要完整内容时，AI 使用 `readFile(output_path, start_line=...)` 分段读取。
- UI 的实时流式显示不受最终回填限幅影响。

## 适用范围

统一输出处理层应覆盖所有工具执行结果，包括：

- `Bash`
- `terminal` 的 `start/send/read`
- `readFile`
- `webfetch`
- `websearch`
- MCP 工具
- 搜索、列表、技能、配置类工具
- 未来新增工具

但并不是所有结果都要落盘。统一层只处理明显超长的文本输出，结构化且较小的结果保持原样，例如：

- `todo` 的结构化列表
- `switchMode` 的状态
- `askUserQuestion` 的结果
- `writeFile/editFile` 的普通小型 diff
- 小型 JSON 对象

## 推荐架构

### 1. 工具本身仍返回完整语义结果

各工具继续返回 `ToolResult`，不要在每个工具里重复实现落盘逻辑。

工具内部只保留必要的内存安全保护。例如：

- `Bash` 可继续限制子进程内存捕获上限，避免进程输出无限占用内存。
- `terminal` 可继续只读取终端 emulator 的 transcript 缓冲。
- `webfetch` 可保留网络响应读取上限。

这些属于工具运行安全，不是最终模型回填策略。

### 2. 工作流统一出口处理

在工具执行完成后、结果落库和回填给模型前，统一调用 `ToolOutputStore`。

建议接入点：

- `StatefulAgentWorkflow.runToolSync`
- `StatefulAgentWorkflow.runToolStream`

这样普通工具和流式工具都会经过同一套输出处理。

### 3. 新增 ToolOutputStore

建议新增文件：

`app/src/main/java/com/aicodeeditor/feature/agent/domain/tool/ToolOutputStore.kt`

职责：

- 判断 `ToolResult` 是否包含超长文本。
- 为超长文本生成 preview。
- 将完整文本写入容器可读路径。
- 返回处理后的 `ToolResult`。

建议输出目录：

`/root/.aicode/tool-output/`

文件命名建议：

`tool-output/<timestamp>-<toolName>-<callIdShort>.log`

注意：

- 文件名要清理非法字符。
- 写入目录必须确保可被 `readFile` 路径映射读取。
- 如果落盘失败，不能让工具整体失败；应退化为只返回 preview，并在元数据中提示保存失败。

## 数据结构

建议内部返回：

```kotlin
data class StoredToolOutput(
    val preview: String,
    val truncated: Boolean,
    val totalChars: Long,
    val outputPath: String? = null,
    val storageError: String? = null
)
```

模型回填 JSON 建议：

```json
{
  "output": "开头...\n\n...[输出过长，完整内容已保存到 /root/.aicode/tool-output/xxx.log]...\n\n结尾...",
  "output_truncated": true,
  "output_total_chars": 123456,
  "output_path": "/root/.aicode/tool-output/xxx.log"
}
```

如果原本就是 JSON 对象，尽量保留原字段，只替换超长字段，并添加旁路元数据：

```json
{
  "tab_id": "term-2",
  "running": true,
  "output": "preview",
  "output_truncated": true,
  "output_total_chars": 123456,
  "output_path": "/root/.aicode/tool-output/xxx.log"
}
```

## 文本提取策略

`ToolOutputStore` 应支持以下 `ToolResult` 形态：

### ToolResult.Success(JsonPrimitive string)

直接处理整个字符串。

短输出：

```kotlin
ToolResult.Success(JsonPrimitive(raw))
```

长输出：

```kotlin
ToolResult.Success(JsonObject(mapOf(
    "output" to JsonPrimitive(preview),
    "output_truncated" to JsonPrimitive(true),
    "output_total_chars" to JsonPrimitive(total),
    "output_path" to JsonPrimitive(path)
)))
```

### ToolResult.Success(JsonObject)

优先处理常见大字段：

- `output`
- `content`
- `text`
- `stdout`
- `stderr`
- `body`
- `result`

处理规则：

- 只处理字符串字段。
- 字符串未超限则不改。
- 字符串超限则替换为 preview。
- 增加同名前缀元数据或统一元数据。

推荐先使用统一元数据，降低复杂度：

```json
{
  "output": "preview",
  "output_truncated": true,
  "output_total_chars": 123456,
  "output_path": "/root/.aicode/tool-output/xxx.log"
}
```

如果对象里有多个超长字段，第一阶段可以只处理最大的一个字段，避免设计过度复杂。第二阶段再支持多字段：

```json
{
  "large_fields": {
    "stdout": {
      "truncated": true,
      "total_chars": 123456,
      "path": "/root/.aicode/tool-output/xxx.stdout.log"
    }
  }
}
```

### ToolResult.Error

默认不落盘。

原因：

- 错误消息通常应该直接进入上下文。
- 如果错误消息异常巨大，再考虑第二阶段支持错误文本限幅。

### JsonArray 或复杂 JSON

第一阶段不做深度遍历，避免破坏结构。

可选策略：

- 如果整体 JSON 序列化超过阈值，保存完整 JSON。
- 回填：
  ```json
  {
    "content": "工具结果过长，已保存到 ...",
    "output_truncated": true,
    "output_path": "..."
  }
  ```

## Preview 策略

推荐沿用现有 `BoundedOutput` 的头尾策略：

- 保留开头 `20_000` 字符。
- 保留结尾 `20_000` 字符。
- 中间插入省略提示。

优点：

- 开头通常包含命令、环境、初始错误。
- 结尾通常包含最终失败原因、退出码、测试摘要。
- 对构建日志更有价值。

后续可以改成更接近 opencode 的行数 + 字节双阈值：

- 默认 `max_lines = 2000`
- 默认 `max_bytes = 50KB`
- preview 按行取前半和后半。

建议第一阶段先复用 `BoundedOutput`，减少改动面；第二阶段再引入可配置阈值。

## 完整输出路径映射

必须确认：

`/root/.aicode/tool-output/...`

可以被现有 `readFile` 读取。

需要检查：

- `WorkspacePathMapper`
- AI 配置目录映射
- 容器路径到宿主路径映射

如果当前不支持，应补齐路径映射，让 AI 能这样读取：

```text
readFile(path="/root/.aicode/tool-output/20260706-terminal-term-2.log", start_line=1)
```

## 具体改动文件

### 新增

`app/src/main/java/com/aicodeeditor/feature/agent/domain/tool/ToolOutputStore.kt`

职责：

- `process(toolName, callId, result): ToolResult`
- `boundText(toolName, callId, text): StoredToolOutput`
- `writeFullOutput(...)`

### 修改

`app/src/main/java/com/aicodeeditor/feature/agent/domain/workflow/StatefulAgentWorkflow.kt`

改动：

- 注入 `ToolOutputStore`。
- 在 `runToolSync` 中，工具执行成功后调用统一处理。
- 在 `runToolStream` 中，`ToolStreamEvent.Completed` 的最终结果也调用统一处理。
- `ToolCallProgress` 不走落盘处理，保持实时 UI 输出逻辑。

### 修改

`app/src/main/java/com/aicodeeditor/feature/agent/domain/tool/container/BackgroundTerminalTools.kt`

改动：

- 移除当前局部 `BoundedOutput` 处理。
- `start/send/read` 返回原始完整语义结果。
- 保留流式捕获逻辑。
- 最终上下文限幅交给 `ToolOutputStore`。

### 修改

`app/src/main/java/com/aicodeeditor/feature/agent/domain/tool/container/ExecuteCommandTool.kt`

第一阶段可选：

- 保留现有 `BoundedOutput`，作为进程输出内存保护。
- 最终结果仍经过统一 `ToolOutputStore`。

第二阶段：

- 明确区分“进程捕获上限”和“模型回填上限”。
- 如果 Bash 原始输出已经在进程层被截断，则 `ToolOutputStore` 只能保存截断后的内容；这一点需要在结果里保留说明。

### 修改

`app/src/main/assets/prompts/60-tools-and-paths.md`

改动：

- 说明工具输出可能是 preview。
- 说明 `output_path` 是完整日志路径。
- 要求 AI 需要完整日志时用 `readFile(output_path, start_line=...)` 分段读取。
- 不要因为输出截断就重复执行构建命令。

## 推荐执行顺序

1. 新增 `ToolOutputStore`。
2. 在 `StatefulAgentWorkflow` 统一出口接入。
3. 移除 `BackgroundTerminalTools` 的局部限幅，避免重复截断。
4. 确认 `/root/.aicode/tool-output` 可被 `readFile` 读取。
5. 更新提示词。
6. 编译验证。
7. 用短输出、长输出、终端输出、Bash 输出分别手动验证。

## 验证计划

### 编译

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

### 短输出

执行一个短命令：

```text
Bash(command="echo hello")
```

预期：

- AI 收到原始短输出。
- 无 `output_path`。
- 无截断提示。

### 长输出

执行长输出命令：

```text
Bash(command="yes line | head -n 10000")
```

预期：

- AI 收到 preview。
- 返回 `output_truncated=true`。
- 返回 `output_total_chars`。
- 返回 `output_path`。
- `readFile(output_path)` 能读到完整内容或工具捕获到的完整内容。

### terminal start/send

启动或发送长输出：

```text
terminal(action="start", command="yes line | head -n 10000")
```

预期：

- UI 流式显示新增输出。
- 最终回填给 AI 的 `output` 是 preview。
- 完整日志落盘。

### webfetch

抓取较长网页：

```text
webfetch(url="...")
```

预期：

- 长正文被统一处理。
- AI 能通过 `output_path` 分段读取完整正文。

## 风险与取舍

### 风险 1：破坏结构化工具结果

避免方式：

- 第一阶段只处理 `JsonPrimitive(string)` 和 `JsonObject` 中明确的大文本字段。
- 不深度遍历复杂 JSON。

### 风险 2：落盘路径无法被 AI 读取

避免方式：

- 优先使用 `/root/.aicode/tool-output`。
- 验证 `readFile` 路径映射。
- 必要时修改 `WorkspacePathMapper`。

### 风险 3：Bash 双重截断

当前 `Bash` 已有 `BoundedOutput`。如果先接统一层，可能出现双重 preview。

处理建议：

- 第一阶段接受这个现状，因为 `Bash` 已安全。
- 第二阶段重构 `Bash`，把进程捕获保护和模型回填保护分离。

### 风险 4：日志文件增长

需要后续清理策略：

- 保留最近 N 天。
- 或每个会话最多 N MB。
- 或启动时清理超过阈值的旧文件。

第一阶段可以先不做自动清理，但文档和路径命名要为清理预留空间。

## 最终建议

采用“统一出口处理 + 完整输出落盘 + preview 回填”的方案。

短期先覆盖所有工具的最终 `ToolResult`，但只处理明显超长文本字段。这样改动集中、收益最大，也不会把每个工具都改成一套不同的截断逻辑。

后续再逐步把 `Bash`、`terminal`、`webfetch` 等工具的内部输出模型统一到同一套 `ToolOutputStore` 配置上。
