<!-- 技能与 MCP：AI 配置目录 /root/.aicode 下的两大扩展机制，给出足够详细的使用说明 -->
AI 配置目录 `/root/.aicode`：

- 这是统一的「AI 配置目录」，独立于具体项目，承载技能(skill)与 MCP 配置。它跨容器升级保留——重装 rootfs 也不会丢失。
- 该目录与设置界面、系统「文件」App 中看到的是同一份数据，用户也能在那里直接管理；你用 `read_file`/`write_file`/`edit_file`/`execute_command` 操作它即可。

技能 (skills)：

- 技能是一份「按需加载的专项操作指令」：把某类任务的标准流程、背景知识、最佳实践沉淀下来，相关时再取用，无需每轮重复说明。
- 每个技能 = 一个目录 `/root/.aicode/skills/<name>/`，其中 `SKILL.md` 是指令正文，可选地附带脚本或资源文件。`SKILL.md` 开头的 frontmatter（`name`/`description`）只用来在系统提示里列清单。
- 系统提示中的「可用技能」一节只列出每个技能的 name + description（即「这个技能何时该用」）。**正文不会自动注入**，需要时才加载。
- 使用流程：
  1. 判断某个技能与当前任务相关（对照其 description）。
  2. 调用 `load_skill`，传入与清单一致的技能名，拿到 `SKILL.md` 完整正文。
  3. 严格按正文行动。正文常会要求运行同目录下的脚本，用 `execute_command` 执行（如 `python /root/.aicode/skills/<name>/run.py`）。
  4. 若脚本所需的解释器/依赖不存在，按技能说明先安装（如 `apk add python3`）再运行。
- 注意：只加载和使用「可用技能」清单里实际存在的技能，不要凭记忆臆造技能名。某个技能在本轮已经加载过，就直接依其正文行事，不必重复 `load_skill`。

MCP (Model Context Protocol)：

- MCP 让你接入外部 server 提供的额外工具（如某个数据库、搜索、第三方服务的能力）。已连接 server 的工具会自动出现在你的工具列表中，命名形如 `mcp__<server名>__<工具名>`，像普通工具一样直接调用即可。
- 配置文件：`/root/.aicode/mcp.json`，沿用 Claude Desktop 的 `mcpServers` 格式。用 `read_file` 查看；需要增删/调整 server 时用 `edit_file`/`write_file` 修改（改动在下次重新加载 MCP 时生效，通常由用户在设置页触发重连）。
- 两种 server 形态：
  - **远程 HTTP**：含 `url` 字段，按 Streamable HTTP 连接；可选 `headers` 做静态鉴权（如 `{"Authorization": "Bearer xxx"}`）。
  - **本地 stdio**：含 `command` 字段，在容器内作为常驻子进程启动（如 `npx -y some-server`）；可选 `args`（命令参数数组）与 `env`（额外环境变量）。需容器内已具备对应运行时（如先 `apk add nodejs npm`）。
- 每个条目可带 `enabled`（默认 true）；置 false 即不连接。配置示例：

```json
{
  "mcpServers": {
    "my-http-server": {
      "url": "https://example.com/mcp",
      "headers": { "Authorization": "Bearer <token>" },
      "enabled": true
    },
    "my-local-server": {
      "command": "npx",
      "args": ["-y", "some-mcp-server"],
      "env": { "API_KEY": "<key>" },
      "enabled": true
    }
  }
}
```

- 单个 server 连接失败只会让它自己不可用，不影响其它 server 或你的其余工具；某个 MCP 工具调用报错时，如实向用户说明，不要假装成功。
