# 日志查看、私有目录挂载与后台运行保活

## 1. 访问容器私有目录 (~/.aicode 等)
Android 沙盒限制了普通 App 访问深层目录，但用户可以通过以下三种方式无缝访问和管理深层配置：
1.  **使用文件管理器挂载 (DocumentsProvider)（推荐）**：App 内部暴露了标准的 `DocumentsProvider` 服务。你可以使用手机自带的“文件”管理器或第三方文件管理应用（如 MT管理器、Solid Explorer 等）找到并挂载 App 的私有根目录。
    *   **挂载后可见的核心目录**：
        *   `projects/`（工作区项目）：存放你在 App 内创建或导入的所有本地代码项目。
        *   `aicode/`（AI 核心配置）：对应容器内的 `/root/.aicode` 目录。内含 `skills/`（扩展技能脚本）、`docs/`（系统文档）与 `mcp.json`（MCP 配置），可在此直接管理自定义技能和配置。
2.  **使用内置终端 (Terminal)**：在主界面侧边栏切到 **终端** 页面。可以直接输入 `cd ~/.aicode` 访问配置文件夹，用 `vim` 或 `cat` 查看和编辑 `skills`、`mcp.json` 等内容。
3.  **直接让 AI 操作**：因为 AI 本身就运行在容器内，用户可以直接对 AI 说：“请帮我把 `~/.aicode/mcp.json` 打印出来给我看”，或者“帮我在 `~/.aicode/skills/` 下写一个新的技能文件”，AI 会直接帮你完美完成！

## 2. 查看 App 运行日志 (Log Files)
App 自动将日志落盘到外部存储，**无需 Root 权限即可直接用手机文件管理器查看**：
*   **文件路径**：`/storage/emulated/0/Android/data/com.aicodeeditor/files/logs/` (即 `Android/data/<包名>/files/logs/`)。

## 3. 系统配置补充
*   **日志等级 (Log Level)**：控制系统打印日志的详细程度，枚举值为 `Verbose`, `Debug`, `Info`, `Warn`, `Error`。遇到 Bug 时建议调成 `Debug`；日常使用保持 `Info`。
*   **工具授权 (Permissions)**：写文件、执行命令等高危授权管理，可随时在列表中点击撤销“永远允许”的授权。
*   **后台运行保活 (Keepalive)**：开启后 Android 系统通知栏会显示常驻服务，防止 AI 在执行超长任务（如编译）切后台时被系统杀掉。
