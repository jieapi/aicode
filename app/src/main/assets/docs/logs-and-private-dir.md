# 日志查看、私有目录挂载与后台运行保活

## 1. 访问容器私有目录 (~/.aicode 等)
Android 沙盒限制了普通 App 访问深层目录，但用户可以通过以下方式无缝访问和管理深层配置：
1.  **使用文件管理器挂载 (DocumentsProvider)（推荐）**：App 内部暴露了标准的 `DocumentsProvider` 服务。你可以使用手机自带的“文件”管理器或第三方文件管理应用（如 MT管理器、Solid Explorer 等）找到并挂载 App 的私有根目录。
    *   **挂载后可见的核心目录**：
        *   `projects/`（工作区项目）：存放你在 App 内创建或导入的所有本地代码项目。
        *   `aicode/`（AI 核心配置）：对应容器内的 `/root/.aicode` 目录。内含 `skills/`（扩展技能脚本）、`docs/`（系统文档）与 `mcp.json`（MCP 配置），可在此直接管理自定义技能和配置。
2.  **使用内置终端 (Terminal)**：在主界面侧边栏切到 **终端** 页面。可以直接输入 `cd ~/.aicode` 访问配置文件夹，用 `vim` 或 `cat` 查看和编辑 `skills`、`mcp.json` 等内容。
3.  **直接让 AI 操作**：因为 AI 本身就运行在容器内，用户可以直接对 AI 说：“请帮我把 `~/.aicode/mcp.json` 打印出来给我看”，或者“帮我在 `~/.aicode/skills/` 下写一个新的技能文件”，AI 会直接帮你完美完成！
4.  **Root 后直接访问私有目录**：如果设备已获取 Android root 权限，也可以绕过挂载，直接进入 App 宿主私有目录 `/data/data/<包名>/files/`（部分系统等价路径为 `/data/user/0/<包名>/files/`）。其中 `projects/` 是本地工作区根目录，`aicode/` 对应容器内的 `/root/.aicode`，`rootfs/` 是容器系统目录。`<包名>` 对应 applicationId：release 为 `com.aicode`，debug（IDE 调试构）为 `com.aicode.debug`——两者私有目录完全隔离，互不可见。

## 2. 查看 App 运行日志 (Log Files)
App 自动将日志落盘到外部存储，同时也提供应用内查看入口：
*   **应用内查看**：进入 **设置 → 日志查看** 可查看最近日志文件；进入 **设置 → MCP 服务器** 后，右上角日志按钮可查看全部日志，编辑某个 MCP 时点击日志按钮可按该 MCP 名称过滤。
*   **文件路径**：`/storage/emulated/0/Android/data/<包名>/files/logs/`。无需 Root 权限即可直接用手机文件管理器查看。`<包名>` 随构建变体变化：release 为 `com.aicode`，debug 为 `com.aicode.debug`。
*   **显示限制**：为避免大日志卡顿，应用内日志查看默认显示当前文件在筛选条件下的最后一部分日志；完整文件仍可在上述目录中查看。

## 3. 系统配置补充
*   **日志等级 (Log Level)**：控制系统打印日志的详细程度，枚举值为 `Verbose`, `Debug`, `Info`, `Warn`, `Error`, `None`（`None` 用作阈值时关闭一切输出）。遇到 Bug 时建议调成 `Debug`；日常使用保持 `Info`。设置页分为「日志等级」二级页与「日志查看」二级页。
*   **工具授权 (Permissions)**：二级页列出当前项目与全局已保存的授权规则，可逐条删除；项目规则可「提升为全局」。系统已预设内置安全白名单，对少量完全无害、只读、不派生子进程的命令（如 ls、pwd、cat、grep、ps、top、git status 等）免弹窗放行；用户在对话中选「始终允许」会生成精准匹配的记忆规则（如 `rm temp.log`、`git pull`）。
*   **后台运行保活 (Keepalive)**：开启后 Android 系统通知栏会显示常驻服务，防止 AI 在执行超长任务（如编译）切后台时被系统杀掉。
