<p align="center">
  <h1 align="center">AiCode</h1>
  <p align="center">
    在 Android 设备上运行的 AI 驱动代码编辑器
    <br />
    内置终端 · AI Agent · MCP 协议
  </p>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg" alt="License" /></a>
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin-purple.svg" alt="Language" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="UI" />
  <img src="https://img.shields.io/badge/MinSDK-26-orange.svg" alt="MinSDK" />
</p>

<p align="center">
  <table>
    <tr>
      <td align="center"><img src="docs/screenshots/home.png" alt="主页 · AI 对话" width="270"/></td>
      <td align="center"><img src="docs/screenshots/terminal.png" alt="终端 · Alpine Linux" width="270"/></td>
    </tr>
    <tr>
      <td align="center">主页 · AI 对话</td>
      <td align="center">终端 · Alpine Linux</td>
    </tr>
  </table>
</p>

---

## Features

- **AI Agent** — 支持 Anthropic、OpenAI 等多家提供商，通过工具系统（文件操作、Shell 执行等）与开发环境深度交互
- **内置终端** — 基于 Termux 组件 + PRoot Alpine Linux 容器，提供完整 Linux 命令行环境
- **MCP 协议** — Model Context Protocol 客户端，连接远程 MCP 服务器动态扩展工具能力
- **代码编辑** — 基于 WebView 的编辑器，支持语法高亮
- **Git 集成** — 内置 Git 操作
- **远程同步** — 支持 SFTP / FTP 工作区同步
- **Markdown 渲染** — AI 对话中实时渲染 Markdown 内容

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt (Dagger) |
| Database | Room |
| Network | Retrofit + OkHttp |
| Async | Kotlin Coroutines / Flow |
| Terminal | Termux terminal-emulator + terminal-view |
| Container | PRoot + Alpine Linux rootfs |
| Remote Sync | SSHJ (SFTP) + Commons Net (FTP) |

## Getting Started

### Prerequisites

- Android 8.0+ (API 26) arm64-v8a 设备
- JDK 17

### Build

```bash
# Debug（默认走 universal 变体）
./gradlew assembleDebug

# Release（需配置签名；构全部三个 flavor）
./gradlew assembleRelease

# 仅构单一 flavor
./gradlew assembleArmsoloRelease     # 仅 arm64-v8a + arm 镜像
./gradlew assembleX86soloRelease     # 仅 x86_64 + x86 镜像
./gradlew assembleUniversalRelease   # arm64-v8a + x86_64，含两套镜像

# Release AAB
./gradlew bundleRelease
```

> 三个 flavor 的产物路径：`app/build/outputs/apk/<flavor>/release/app-<flavor>-release.apk`

<details>
<summary>Release 签名配置</summary>

在 `app/keystore.properties` 中添加：

```properties
storeFile=aicode.jks
storePassword=your_password
keyAlias=your_alias
keyPassword=your_key_password
```

</details>

### Test

```bash
./gradlew test                    # 单元测试
./gradlew connectedAndroidTest    # 集成测试
```

## Project Structure

```
app/src/main/java/com/aicode/
├── core/                # 核心模块（主题、通用组件）
├── feature/
│   ├── agent/           # AI Agent（提示词、MCP、工具注册、多提供商适配）
│   ├── git/             # Git 集成
│   ├── settings/        # 应用设置
│   ├── terminal/        # 终端模拟与会话管理
│   └── workspace/       # 工作区与文档管理
├── AIEditorApp.kt       # Application 入口
└── MainActivity.kt      # 主 Activity
```

## Known Limitations

- `targetSdk` 锁定为 28 以绕过 Android 10+ W^X 策略，使 PRoot 可执行。**无法上架 Google Play**。
- Release 按 CPU/容器镜像拆三个 variant：
  - `armsolo`：仅 `arm64-v8a` + arm 镜像（真机首选）
  - `x86solo`：仅 `x86_64` + x86 镜像（模拟器 / Chromebook）
  - `universal`：`arm64-v8a` + `x86_64`，含两套镜像（通用但体积更大）
  - 容器镜像随系统 ABI 选择，错架构设备安装单架构包后无法运行 PRoot。

## Acknowledgements

- [OpenCode](https://github.com/anomalyco/opencode) — 终端 AI 编码工具，本项目的核心灵感来源
- [Termux](https://github.com/termux/termux-app) — Android 终端模拟器，提供了终端组件与 PRoot 方案
- [Kelivo](https://github.com/Chevey339/kelivo) — 跨平台 LLM 聊天客户端，AI 对话界面设计参考

## License

This project is licensed under the [GPL-3.0](LICENSE).
