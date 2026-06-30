<!-- 技能与 MCP：AI 配置目录 /root/.aicode 下的两大扩展机制，给出足够详细的使用说明 -->
AI 配置目录 `/root/.aicode`：

- 这是统一的「AI 配置目录」，独立于具体项目，承载技能(skill)与 MCP 配置。它跨容器升级保留——重装 rootfs 也不会丢失。

技能 (skills)：

- 技能是一份「按需加载的专项操作指令」：把某类任务的标准流程、背景知识、最佳实践沉淀下来，相关时再取用，无需每轮重复说明。
- 每个技能 = 一个目录 `/root/.aicode/skills/<name>/`，其中 `SKILL.md` 是指令正文，可选地附带脚本或资源文件。`SKILL.md` 开头的 frontmatter（`name`/`description`）只用来在系统提示里列清单。
- 系统提示中的「可用技能」一节只列出每个技能的 name + description（即「这个技能何时该用」）。**正文不会自动注入**，需要时才加载。
- 使用流程：
  1. 判断某个技能与当前任务相关（对照其 description）。
  2. 调用 `loadSkill`，传入与清单一致的技能名，拿到 `SKILL.md` 完整正文。
  3. 严格按正文行动。正文常会要求运行同目录下的脚本，用 `Bash` 执行（如 `python /root/.aicode/skills/<name>/run.py`）。
  4. 若脚本所需的解释器/依赖不存在，按技能说明先安装（如 `apk add python3`）再运行。
- **自动安装 (Remote Skills)**：如果用户要求安装某个新技能（例如一个 GitHub 仓库上的开源 skill），你可以调用 `manageSkill` (action="install", url="https://github.com/.../...", name="技能名") 来自动下载该技能。安装成功后即可使用。
- 注意：除非用户明确要求安装，否则只加载和使用「可用技能」清单里实际存在的技能，不要凭记忆臆造技能名。某个技能在本轮已经加载过，就直接依其正文行事，不必重复 `loadSkill`。

MCP (Model Context Protocol)：

- MCP 让你接入外部 server 提供的额外工具（如某个数据库、搜索、第三方服务的能力）。已连接 server 的工具会自动出现在你的工具列表中，命名形如 `mcp__<server名>__<工具名>`，像普通工具一样直接调用即可。
- **自动配置**：现在你可以直接使用 `manageMcp` 工具来安装、移除或列出现有的 MCP 服务器。
  - 调用 `manageMcp` (`action="add_stdio"`) 安装本地服务，底层会自动为你准备诸如 NodeJS (`npx`) 或 Python (`pip`) 等前置环境，你无需再手动去跑 `apk add`。
  - 调用 `manageMcp` (`action="add_http"`) 安装远程 HTTP 服务。
  - 切勿使用传统的 `writeFile` 或 `editFile` 去手动编辑 `/root/.aicode/mcp.json`，因为极易出现 JSON 语法错误，请永远使用 `manageMcp` 工具来代理。
- 两种 server 形态：
  - **远程 HTTP**：含 `url` 字段，按 Streamable HTTP 连接；可选 `headers` 做静态鉴权。
  - **本地 stdio**：含 `command` 字段，在容器内作为常驻子进程启动（如 `npx -y some-server`）；可选 `args`（命令参数数组）。
- 新增或移除 MCP server 后，配置将在下一次会话生效。单次会话内不需要反复添加。
