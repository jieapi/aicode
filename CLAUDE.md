# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## General Rules
- 永远使用中文回复 (Always reply in Chinese).
- 优先使用已有工具进行文件操作，比如读取、修改文件 (Prioritize using existing tools for file operations, such as reading and modifying files).

## Asset Synchronization
项目中的 `app/src/main/assets/prompts/` 和 `app/src/main/assets/docs/` 是 AI Agent 的核心知识来源，必须与代码保持同步：

- **工具变更 → 同步 prompts**：当 `app/src/main/java/com/aicode/feature/agent/domain/tool/` 下的工具新增、删除、重命名或参数签名变化时，必须同步更新 `app/src/main/assets/prompts/60-tools-and-paths.md` 中的工具描述，确保模型看到的工具定义与实际注册一致。
- **设置功能变更 → 同步 docs**：当 `app/src/main/java/com/aicode/feature/settings/` 下的设置项新增、删除或行为变化时，必须同步更新 `app/src/main/assets/docs/` 下对应的文档文件：
  - AI 服务商与模型配置变更 → `providers-and-models.md`
  - MCP 服务器与扩展技能配置变更 → `mcp-and-skills.md`
  - 日志、私有目录与保活设置变更 → `logs-and-private-dir.md`
  - 远程服务器与同步设置变更 → `remote-servers.md`
  - 工作区与模式配置变更 → `app-settings-guide.md`

## Git 提交规范

项目采用 **Conventional Commits**，由 `.githooks/commit-msg` 在本地校验（启用见仓库根 `.githooks/`）。格式：

```
<type>(<scope>): <subject>

<可选正文，空行隔开>
```

- **type** ∈ `feat | fix | refactor | docs | style | chore | ci | build | perf | test`
- **scope** 可选，建议用功能模块：`agent | settings | terminal | workspace | git | ui | mcp | db | core | deps`
- **subject** 一行简述，中英文均可，句末不加句号。
- 跳过校验（仅紧急）：`git commit --no-verify ...`

示例：`feat(agent): 支持流式工具调用` / `fix(settings): 修复 provider 保存时校验失败` / `ci: 删除签名校验步骤`

## 分支与改动工作流

**原则：不在 `main` 上直接写功能，`main` 只接收已验证的分支。** 本仓库无灰度、靠 GitHub Release 分发且发出去即终态，`versionCode` 又由 git commit count 自动生成——直接在 `main` 上堆改动会污染发版线和 commit count，改坏不好回退。

- **改动分档**：
  - **新功能 / 行为变化 / 多文件改动**：新建分支 `feat/xxx`，改完走发版流程（属行为变化，发版时按新功能档升 `x.Y.0` 且须先发 RC；但提交本身**不要**动 `versionName`，见版本号规范）。
  - **纯 bug 修复 / 重构**：新建分支 `fix/xxx`；极小且不触碰启动/容器的可在 `main` 上快速修，但优先走分支以便回退与验证。
  - **纯文档 / typo / 资源文案**：可直接在 `main` 上改。
- **改动前先定分支**：接到改动需求时，先判断当前所在分支是否适合承载这次改动——若当前分支主题与改动无关（如在 `fix/a` 上做 `fix/b` 的事），不要直接动手，先询问是否新建分支。同时检查已有分支（`git branch`）有没有主题相近的：若有合适的，可把改动放到那个分支而非另开新枝；若没有再新建。避免同一主题散落在多个分支、或无关改动混进当前分支。
- **命名**：`feat/` 与 `fix/` 前缀 + 短主题，如 `feat/session-model`、`fix/provider-injection`。
- **从哪拉分支**：从 `main` 最新拉分支，不要从一个功能分支再分岔（除非确实依赖它）。一份功能一个分支，**不要在同一分支混入不相关改动**。
- **提交前必跑冒烟**：改完编译型代码（`.kt` / `.gradle.kts` / `AndroidManifest.xml`）→ 提交前默认 `./gradlew :app:assembleUniversalDebug` 验证可编译（**勿跑 `assembleDebug`/`assembleRelease` 三 flavor**，详见 Build and Run）。**禁止推未编译验证的分支**——历史教训：构造函数加参数却漏改 DI `@Provides` 的传参，导致整条分支编译不过。
- **不留净效果为零的提交**：改主意用 `git commit --amend` 或 `git rebase -i` 合并精简，不要留「加了又删」的来回提交。注意 rebase/squash 会改写历史，可能让 commit count 回退，触发 CI 的 versionCode 单调校验（见版本号规范）。
- **合并入 main**：在 GitHub 开 PR 合并，或本地确认无冲突后合回 `main` 再 push；合并前确保分支冒烟通过。

## 版本号规范

- **唯一来源**：`app/build.gradle.kts` 的 `versionName`（`主.次.修`，如 `1.0.0`，手写）与 `versionCode`（由 `gitCommitCount()` 从 git 提交数自动生成，无需手写）。
- **何时递增**：**`versionName` 只在发版的那一刻改一次，平时提交一律不碰**——平时提交不要动 `versionName`，避免每次改完功能都想去改版本号（提交时碰它没意义，真正的版本号是在打 tag 发版时确定的）。要发版时按下一版要承载的内容定档：
  - 次版本号（中间位）：本发版周期含新增功能 / 行为变化 → 升 `x.Y.0`。
  - 修订号（末位）：本发版周期仅 bug 修复 / 纯文档 / 重构 → `x.y.Z`。
- **`versionCode` 无需手动维护**：随每次 git 提交自动 +1（commit count 单调递增）。rebase/squash 改写历史可能让 commit count 变小，CI（`android-release.yml` 的 Verify versionCode monotonic 步骤）会校验当前 > 上个 Release 防回退。
- **与 Release 绑定**：发版时打的 git tag 必须与 `versionName` 完全一致——tag 写 `v<versionName>`（如 versionName=`1.0.0` → tag=`v1.0.0`）。CI 触发靠 tag 名 `v*`，错了会发到错误版本号上。

## 发版流程（RC 判定）

本项目靠 GitHub Release 分发且无灰度，发出去即终态，RC 是主要兜底。发版前按改动面判断是否先发 RC：

- **必须先发 RC**：本发版周期含新功能 / 行为变化（定档 `x.Y.0`）；或构建链路 / 签名 / flavor / CI 改动；或容器镜像、PRoot、ABI 相关改动。
- **可直接发正式**：本发版周期仅纯文档 / typo / 资源文案（定档 `x.y.Z`，无行为变化）。
- **看改动面**：本发版周期仅纯 bug 修复（定档 `x.y.Z`）——小改直接正式，触碰启动/容器的仍先 RC。

> 注：这一档只影响本发版版本号定档（`x.Y.0` vs `x.y.Z`）与是否先发 RC，**不要求在改动提交时改 `versionName`**——版本号等到实际发版时（按下面操作步骤第 1 步）一并改、commit、再打 tag。

### 操作步骤

1. **此时才改 `versionName`**：按本发版周期承载的内容在 `app/build.gradle.kts` 定档（新功能/行为变化 → `x.Y.0`；仅 bug 修复/文档 → `x.y.Z`），设好 `versionName` 后 commit。这是版本号唯一的改动时机——平时功能提交不要碰它。
2. `git tag v<versionName>-rc1`（如 `v1.2.0-rc1`）并 push。CI 校验 tag 版本部分 == `versionName`，构三 flavor 发 Release。
3. **真机装 rc 包**，至少跑通 AI 对话 + 终端 + 容器启动三条主线。
4. 有问题 -> 修 -> 升 rc 序号（`-rc2`）重发；没问题 -> `git tag v<versionName>` push 发正式。

RC 与正式版共享同一 `versionName`，`versionCode` 由 commit count 自动生成，天然 rc1 < rc2 < ... < 正式版，用户从任意 rc 都能直接升级到正式版，无需手动管 versionCode。

> RC 发出后必须真机验证再转正，否则 RC 无意义。

## Build and Run

This is an Android application built with Kotlin, Jetpack Compose, and Hilt. It uses Gradle as the build system.

- **Build the project:** `./gradlew build`
- **Assemble Debug APK:** `./gradlew assembleDebug`
- **单 flavor 冒烟（AI 改完代码默认跑这个）：** `./gradlew :app:assembleUniversalDebug` —— `assembleDebug`/`assembleRelease` 是 flavor 聚合任务，会把 universal/armsolo/x86solo 三个 APK 各构一遍（三倍 Kotlin 编译 + 资源处理，慢）。AI 改完**编译型代码**（`.kt` / `.gradle.kts` / `AndroidManifest.xml`）后、提交前，默认只构 **universal debug** 单个 APK 做冒烟验证，不要触发全量三 flavor。仅改文档/资源/纯 `.md` 时可跳过。完整发版才用 `assembleRelease` 构三个。
- **Assemble Release APK:** `./gradlew assembleRelease` —— 按容器镜像/CPU 拆三个 flavor，输出到 `app/build/outputs/apk/<flavor>/release/app-<flavor>-release.apk`（flavor ∈ universal/armsolo/x86solo）
- **Assemble Release AAB:** `./gradlew bundleRelease` —— 输出到 `app/build/outputs/bundle/<flavor>/release/app-<flavor>-release.aab`
- **Run Unit Tests:** `./gradlew test`
- **Run Android Tests:** `./gradlew connectedAndroidTest`

### Release Packaging & Signing
The release signing configuration is automatically handled in `app/build.gradle.kts`:
- **Keystore File:** `app/aicode.jks`
- **Credentials:** Loaded from `app/keystore.properties` (`storePassword`, `keyAlias`, `keyPassword`).
- **Target ABI:** Only `arm64-v8a` is packaged to keep APK size reasonable while supporting Termux/PRoot rootfs.

*Note: The project locks `targetSdk = 28` intentionally to allow PRoot execution (W^X policy bypass on Android 10+).*

## Architecture

The application is structured using a feature-based architecture with Domain-Driven Design (DDD) principles. It relies heavily on Jetpack Compose for the UI, Hilt for Dependency Injection, and Kotlin Coroutines/Flows for asynchronous operations.

### Key Components

- **App Core:** `AIEditorApp` initializes core services like `FileLogger`, `TerminalKeepaliveService`, and `McpManager`.
- **Feature Modules:** Code is organized by feature under `app/src/main/java/com/aicode/feature/`:
    - `agent`: The core AI agent system. Includes prompt management, MCP (Model Context Protocol) integration, tool registry (file tools, shell execution, etc.), permission handling, and adapters for different AI providers (Anthropic, OpenAI).
    - `git`: Git integration and operations.
    - `settings`: Application configuration, including AI provider setup, logging, and keepalive settings.
    - `terminal`: Terminal emulation and session management, leveraging Termux components (`terminal-emulator`, `terminal-view`) and PRoot via `LinuxContainerEngine`.
    - `workspace`: Workspace and document provider management.

### Database

The app uses Room for local database storage, primarily found in `feature/agent/data/local/database/AgentDatabase.kt` and related DAOs (e.g., `ChatSessionDao`, `AgentMessageDao`).

**Database Migrations:**
We use a custom, lightweight file-based migration system (`MigrationLoader.kt`).
To update the database schema:
1. Increment the database version in `AgentDatabase.kt`.
2. Create a new SQL file in `app/src/main/assets/migrations/` named `{VERSION}_description.sql` (e.g., `8_add_new_table.sql`).
3. Add the necessary DDL/SQL statements to this file. The system will automatically execute it on startup and record it in the `migration_history` table.

### AI Agent & Tools

The AI agent interacts with the environment through a tool system (`feature/agent/domain/tool/`). Available tools include file operations (`FileTools.kt`), shell execution (`ExecuteCommandTool.kt`), and asking user questions. Tools are registered and managed via `ToolRegistry`. Permission to execute certain tools (like shell commands) is governed by `ToolPermissionManager` and `ToolPermissionPolicyEngine`.

### MCP (Model Context Protocol)

The app implements an MCP client (`feature/agent/domain/mcp/`) to connect to remote servers and dynamically register tools provided by those servers.

### Dependency Injection

Hilt is used extensively. Feature modules define their own DI modules (e.g., `AgentModule.kt`, `RepositoryModule.kt`) to provide interfaces to their implementations.