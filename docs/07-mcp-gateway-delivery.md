# MCP 网关交付说明

> 状态：V1 对接草案
> 负责人：项目维护者
> 适用版本：V1
> 最后更新：2026-06-09
> 阅读顺序：02-07
> 文档职责：交付给 MCP 网关同事，说明 MCP 网关在动态授权链路中需要实现的能力、提供的接口、调用的外部接口和禁止行为。

## 交付目标

MCP 网关在当前架构中承担三类职责：

| 平面 | 需要实现的能力 |
| --- | --- |
| 管理面 | 向管理前端提供当前 MCP 全量工具列表 |
| 运行时 | 接收 Agent 的 MCP 工具调用，携带 `tokenId + toolId` 请求策略中心鉴权 |
| 执行面 | 仅在 `ALLOW` 后获取 Cookie、注入 MCP Server 调用并返回结果 |

总体链路见 [项目总体架构](01-project-overall-architecture.md)。授权决策规则见 [策略中心功能规格](02-policy-center/01-policy-center-spec.md#授权决策)。策略中心接口见 [API 契约](02-policy-center/02-api-contract.md)。

## MCP 网关需要提供的能力

### 1. 管理面工具列表

MCP 网关需要向管理面前端提供当前 MCP 全量工具列表。

要求：

- `toolId` 必须全局唯一。
- 返回的是当前 MCP 全量工具列表，不按 Agent 过滤。
- 工具名称、描述、状态、所属 MCP Server 等目录信息由 MCP 网关负责。
- 工具下线、禁用、改名和版本变化等目录语义由 MCP 网关负责。
- 策略中心不复制 MCP 工具目录，只保存 `agentId + toolId + authMode` 绑定关系。
- 前端从该全量工具列表中选择部分工具绑定到 Agent，见 [管理面前端接口](02-policy-center/06-admin-frontend-api.md)。

接口 URL、请求参数、分页和鉴权方式由 MCP 网关责任人提供，本文不重复定义。


## MCP 网关需要调用的外部接口

### 1. 请求策略中心授权决策

```http
POST /internal/authorization-decisions
```

请求：

```json
{
  "tokenId": "agent-a:user-42:conversation-99",
  "toolId": "tool-a"
}
```

响应由策略中心返回：

```text
decision: ALLOW | AUTHORIZATION_REQUIRED | DENY
reason:
- NO_AUTH_REQUIRED
- CONVERSATION_AUTHORIZED
- USER_AUTHORIZATION_REQUIRED
- TOOL_NOT_BOUND
- INVALID_TOKEN_ID
- POLICY_STORE_UNAVAILABLE
- AUTHORIZATION_STORE_UNAVAILABLE
```

处理规则：

| decision | MCP 网关行为 |
| --- | --- |
| `ALLOW` | 继续按 `tokenId` 向 Agent 网关获取 Cookie，并调用 MCP Server |
| `AUTHORIZATION_REQUIRED` | 向 Agent 返回未授权状态和 `toolId`，不获取 Cookie，不调用 MCP Server |
| `DENY` | 终止工具调用，不触发用户授权页面，不获取 Cookie，不调用 MCP Server |

策略中心超时、网络错误或 `5xx` 时，MCP 网关必须按失败关闭处理，不得降级放行。

### 2. 按 tokenId 获取关联 Cookie

MCP 网关仅在策略中心返回 `ALLOW` 后，才允许调用 Agent 网关的 Cookie 获取接口。

要求：

- 使用 `tokenId` 向 Agent 网关获取关联 Cookie。
- Agent 网关接口契约由 Agent 网关责任人提供。
- MCP 网关不得在授权判断前获取 Cookie。
- MCP 网关不得长期保存 Cookie。
- Cookie 只允许用于本次获准 MCP 工具调用。



## 审计与日志(暂不考虑)

MCP 网关需要记录以下事件：

- 收到 Agent 工具调用请求。
- 向策略中心提交 `tokenId + toolId`。
- 收到策略中心决策和 reason。
- `ALLOW` 后获取 Cookie 的动作。
- 调用 MCP Server 的开始和结束。
- `AUTHORIZATION_REQUIRED` 或 `DENY` 的终止结果。

日志字段至少包含：

```text
traceId
tokenId
toolId
decision
reason
mcpServer
resultStatus
occurredAt
```

日志不得记录 Cookie、业务 Token、密钥或完整敏感业务响应。

## 验收清单

- 管理面可以通过 MCP 网关获取当前 MCP 全量工具列表。
- MCP 工具调用前必须先提交 `tokenId + toolId` 到策略中心。
- `ALLOW` 后才获取 Cookie 并调用 MCP Server。
- `AUTHORIZATION_REQUIRED` 时返回 Agent 未授权状态和 `toolId`。
- `DENY` 时不获取 Cookie、不调用 MCP Server。
- 策略中心异常、超时或 `5xx` 时不获取 Cookie、不调用 MCP Server。
- 被策略中心判定未绑定的工具不得进入人在回路授权。
- MCP 网关日志不得记录 Cookie、业务 Token 或密钥。
- 工具调用日志必须包含 `tokenId`、`toolId`、决策结果和 `traceId`。

## 假设与边界

- MCP 网关已有管理面工具列表接口，本文只描述语义要求，不定义具体 URL。
- MCP 网关已有或将提供 Agent 调用 MCP 工具的入口，本文只要求其能提取并传递 `tokenId + toolId`。
- Agent 网关的 Cookie 获取接口由 Agent 网关责任人提供，本文只约束 MCP 网关调用时机。
- 当前版本不支持跨对话授权、长期授权或按部门过滤工具。
