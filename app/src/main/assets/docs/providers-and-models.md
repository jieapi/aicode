# AI 服务商 (Providers) 与模型配置指南

点击菜单进入“设置” -> “Providers”，可以添加或修改大模型 API 配置，包含以下具体字段：

## 1. 配置字段说明
*   **名称 (Name)**：文本框。给服务商取个别名（如“我的中转 API”）。
*   **类型 (Type)**：选项框。一般为 `openai` 或 `anthropic`。决定发包协议。第三方中转站基本都选 `openai`。
*   **API Key**：文本框。填入秘钥（如 `sk-xxx`）。
*   **Base URL (接口地址)**：文本框。填 API 根域名（如 `https://api.openai.com`）。不要带尾部的 `/v1` 或具体的 path。
*   **API Path (接口路径)**：文本框。紧跟 Base URL 后的请求路径，默认是 `/chat/completions`。
*   **Use Response API**：开关。非标准解析模式，默认**关闭**。

## 2. 模型管理
*   **模型 (Models)**：可以点击拉取按钮去同步服务商提供的模型列表，也可以手动输入（如 `gpt-4o`, `claude-3-5-sonnet`）后点击添加。
*   **默认模型 (Default Model)**：下拉框。选择该服务商默认选用的出战模型。
