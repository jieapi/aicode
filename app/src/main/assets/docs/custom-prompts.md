# 自定义提示词 (Custom Prompts)

AiCode 的系统提示词支持用户自定义覆盖。默认提示词随 App 内置、App 升级时自动更新；你只需把想改的片段放进自定义目录即可覆盖，无需改动 App 本体。

## 1. 目录结构

提示词文件存放在 App 私有配置目录 `~/.aicode/`（容器内路径 `/root/.aicode/`，Android 宿主对应 `filesDir/aicode/`），结构如下：

```
~/.aicode/
├── prompts/          默认提示词（App 启动时从内置全量释放，升级自动覆盖）
│   ├── 00-identity.md
│   ├── 10-communication.md
│   ├── 15-project-rules.md
│   ├── 20-coding-discipline.md
│   ├── 30-comments.md
│   ├── 40-approach.md
│   ├── 50-safety.md
│   ├── 60-tools-and-paths.md
│   ├── 70-skills-and-mcp.md
│   ├── 80-plan-mode.md
│   └── 81-auto-mode.md
├── prompts.custom/   用户自定义覆盖（同名即覆盖，App 升级不碰这里）
│   └── 50-safety.md  只放你想覆盖的片段
├── skills/
└── docs/
```

## 2. 加载优先级

对每个片段文件，按以下顺序查找，命中即用，不再往后找：

1. `~/.aicode/prompts.custom/<同名文件>` —— 用户自定义覆盖（最高优先级）
2. `~/.aicode/prompts/<同名文件>` —— 本地默认副本
3. App 内置 `assets/prompts/<同名文件>` —— 兜底

也就是说：`prompts.custom/` 里有的片段用你的版本；没有的片段自动回落到 `prompts/` 里的默认版本。

## 3. 如何自定义

### 只想改某几个片段（推荐）
1. 在 `~/.aicode/prompts.custom/` 目录下（不存在则手动创建）放入你想覆盖的片段文件，文件名必须与默认片段**完全一致**（如 `50-safety.md`）。
2. 编辑文件内容为你想要的提示词。
3. **重启 App 后生效**。提示词在 App 进程启动时加载并缓存，新开会话不会重新读取——必须重启 App 才会加载修改后的自定义提示词。

不需要把所有片段都复制过去——只放你想改的，其余自动用默认版本。

### 想重写全部
把所有片段文件都放进 `prompts.custom/`，每个都用自己的版本即可。

## 4. App 升级时的行为

* **`prompts/` 目录**：App 每次启动都会把内置提示词全量覆盖释放到这里。App 升级后，默认提示词随之更新，无需你做任何操作。
* **`prompts.custom/` 目录**：App 永远不会自动写入或删除这里的文件。你的自定义覆盖在升级后完整保留。

因此：你在 `prompts/` 里直接改的内容会在下次升级时被覆盖丢失；**要持久化自定义，请把文件放在 `prompts.custom/`**。

## 5. 片段说明

| 文件名 | 内容 |
|---|---|
| `00-identity.md` | AI 身份与角色定义 |
| `10-communication.md` | 沟通与回复风格规范 |
| `15-project-rules.md` | 项目规则加载约定（AGENTS.md / CLAUDE.md） |
| `20-coding-discipline.md` | 编码纪律与规范 |
| `30-comments.md` | 代码注释规范 |
| `40-approach.md` | 工作方式与流程 |
| `50-safety.md` | 安全与可信边界 |
| `60-tools-and-paths.md` | 工具说明与路径约定 |
| `70-skills-and-mcp.md` | 技能与 MCP 集成说明 |
| `80-plan-mode.md` | PLAN 计划模式专属约束 |
| `81-auto-mode.md` | AUTO 自动模式专属约束 |

## 6. 编辑方式

* **终端内编辑**：在 AiCode 终端中直接用 `vi` / `nano` 等编辑器修改 `~/.aicode/prompts.custom/` 下的文件。
* **AI 协助**：在会话中让 AI 帮你创建或修改自定义提示词文件（AI 的文件工具可直接读写该目录）。
* **外部文件管理器**：通过 Android 系统文件管理器访问 App 私有目录（需拥有 root 权限，路径见第 1 节）。

## 7. 重置

删除 `~/.aicode/prompts.custom/<对应文件>` 即可恢复该片段为默认版本。删除整个 `prompts.custom/` 目录则全部恢复默认。

## 8. 注意事项

* 自定义片段的文件名必须与默认片段**完全一致**（区分大小写），否则不会被识别为覆盖。
* 提示词在整个 App 进程生命周期内只加载一次并缓存，修改文件后需**重启 App**（不是新开会话）才会生效。
* `60-tools-and-paths.md` 等片段会随工具变更而更新，如果你覆盖了它，升级后不会自动获得新版工具描述——如需更新，请手动同步或删除你的自定义版本让默认版本重新生效。
