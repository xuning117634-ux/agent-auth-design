# 业务后端与业务 Agent 接入指南（参考材料）

> 状态：V1 参考材料
> 适用对象：业务后端、业务 Agent、联调负责人
> 最后更新：2026-06-11
> 阅读顺序：5-99
> 文档职责：保留业务后端与业务 Agent 接入的早期完整参考说明。新业务优先阅读 [开发人员详细接入文档](02-developer-integration-api-guide.md)；财经 Agent 定制接入优先阅读 [财经 Agent 授权预检与批量授权接口](03-finance-agent-authorization-apis.md)；策略中心接口字段以 [策略中心对外接口参考](../02-policy-center/08-external-api-reference.md) 为准。

## 1. 方案总览

当前安全方案的主链固定为：

```text
用户 -> 业务后端 -> Agent 网关 -> Agent -> MCP 网关
     -> 权限策略中心 -> MCP Server -> 业务 API
```

业务后端和业务 Agent 接入时只需要理解三个核心原则：

1. 业务后端负责业务会话、用户上下文、授权页面和对话结束清理。
2. 业务 Agent 负责执行任务、调用 MCP 网关、未授权时保存检查点并轮询授权状态。
3. Cookie、长期 Token、业务密钥等原始凭证不进入业务 Agent，只有 MCP 网关在策略中心明确 `ALLOW` 后，才能按 `tokenId` 向 Agent 网关获取 Cookie 并注入 MCP Server 调用。

`tokenId` 由 Agent 网关生成

业务 Agent 只透传 `tokenId`，不要自行生成、修改或解析。业务后端在对话结束清理时，如果不希望理解 `tokenId` 拼接规则，可以使用外部清理接口传递 `agentId + userId + conversationId`。

## 2. 角色边界

| 角色 | 需要接入的模块 | 核心职责 | 禁止行为 |
| --- | --- | --- | --- |
| 业务后端 | Agent 网关、权限策略中心 | 提交用户和对话上下文，展示授权页面，确认授权，清理对话授权 | 不生成 `tokenId`，不自行判断工具授权，不让浏览器直连策略中心 |
| 业务 Agent | Agent 网关、MCP 网关、权限策略中心 | 接收 `tokenId`，调用 MCP 网关，未授权时挂起、轮询、恢复 | 不接触 Cookie，不绕过 MCP 网关调用 MCP Server，不把状态查询当作最终鉴权 |
| Agent 网关 | 由 Agent 网关团队提供 | 路由 Agent、生成 `tokenId`、隔离保存 Cookie、转发授权请求 | 不执行工具授权决策 |
| MCP 网关 | 由 MCP 网关团队提供 | 接收工具调用，向策略中心提交 `tokenId + toolId`，按决策放行或拒绝 | 未 `ALLOW` 前不得获取 Cookie 或调用 MCP Server |
| 权限策略中心 | 当前仓库提供 | 管理 Agent-工具策略，处理当前对话授权，返回授权决策 | 不生成 `tokenId`，不保存 Cookie，不调用 MCP 工具 |

## 3. 业务后端接入

### 3.1 发起 Agent 请求(Agent 在 Agent 网关注册后,提供调用地址)

业务后端收到用户业务请求后，向 Agent 网关提交：

```text
userId
conversationId
Agent 请求内容
Cookie
```

接入要求：

- `userId` 必须能稳定标识当前登录用户。
- `conversationId` 由业务后端维护，同一个业务对话内保持不变。
- `userId` 和 `conversationId` 不得包含分隔符 `:`。
- Cookie 只交给 Agent 网关隔离保存，不传给业务 Agent。
- Agent 网关接口 URL、请求体和鉴权方式由 Agent 网关责任人提供，本文只约束接入语义。

Agent 网关收到请求后，会确定全局唯一 `agentId`，生成 `tokenId`，并把请求和 `tokenId` 转发给目标 Agent。

### 3.2 接收授权请求并展示页面

当工具需要用户授权且当前对话尚未授权时，链路会变成：

```text
MCP 网关 -> Agent：未授权 + toolId
Agent -> Agent 网关：tokenId + toolId + 授权请求
Agent 网关 -> 业务后端：转发授权请求
业务后端 -> 用户：展示授权页面
```

业务后端需要展示一个 1 分钟有效的授权页面。V1 支持“有效期内授权”和“每次调用授权”两类授权含义，页面文案应按工具标签表达清楚：

```text
USER_AUTH_REQUIRED      = 允许在授权有效期内调用该工具
PER_CALL_AUTH_REQUIRED  = 仅允许下一次重试调用该工具
```

接入要求：

- 授权页面和服务端授权会话最长有效 1 分钟。
- V1 授权页面暂只展示本次对话授权，不提供 7 天、30 天或跨对话授权选项；相关能力开发中。
- 浏览器页面不能直接调用策略中心，必须由业务后端服务端在用户确认后调用策略中心。
- 如果授权请求已经超时，业务后端不得再调用授权确认接口。

### 3.3 用户确认后写入授权

用户明确同意后，业务后端调用策略中心：

```http
POST /internal/conversation-authorizations
Content-Type: application/json
X-Trace-Id: trace-20260611-001

{
  "tokenId": "agent-a:user-42:conversation-99",
  "toolId": "crm.customer.delete",
  "expiresInSeconds": 3600
}
```

成功响应：

```json
{
  "status": "AUTHORIZED",
  "tokenId": "agent-a:user-42:conversation-99",
  "toolId": "crm.customer.delete"
}
```

处理要求：

- 重复提交同一 `tokenId + toolId` 是幂等的。
- `expiresInSeconds` 可选，单位秒；缺失时使用策略中心默认 TTL，且不得超过策略中心最大 TTL。
- `USER_AUTH_REQUIRED` 工具在 TTL 内持续允许同一 `tokenId + toolId`。
- `PER_CALL_AUTH_REQUIRED` 工具只允许下一次重试，命中后授权记录会被策略中心消费删除。
- `409 TOOL_NOT_BOUND` 表示工具未绑定该 Agent，不能继续授权。
- `409 AUTHORIZATION_NOT_REQUIRED` 表示工具无需用户授权，不应写入当前对话授权。
- `503 POLICY_STORE_UNAVAILABLE` 或 `503 AUTHORIZATION_STORE_UNAVAILABLE` 必须提示系统暂不可用，不得当作授权成功。

### 3.4 对话结束或删除时清理授权

业务后端在对话结束或删除时可以清理当前对话授权

如果业务后端已持有 `tokenId`，调用内部清理接口：

```http
POST /internal/conversation-authorizations/cleanup
Content-Type: application/json
X-Trace-Id: trace-20260611-002

{
  "tokenId": "agent-a:user-42:conversation-99"
}
```

如果业务后端不希望理解 `tokenId` 拼接规则，调用外部清理接口：

```http
POST /external/conversation-authorizations/cleanup
Content-Type: application/json
X-Trace-Id: trace-20260611-003

{
  "agentId": "agent-a",
  "userId": "user-42",
  "conversationId": "conversation-99"
}
```

成功响应：

```json
{
  "status": "CLEARED",
  "deletedGrantCount": 2
}
```

清理接口是幂等的，`deletedGrantCount = 0` 也表示清理完成。

## 4. 业务 Agent 接入

### 4.1 接收请求并调用 MCP 网关

业务 Agent 从 Agent 网关接收：

```text
tokenId
用户请求上下文
Agent 任务参数
```

业务 Agent 调用 MCP 网关时，需要携带：

```text
tokenId
toolId
工具参数
```

接入要求：

- MCP 网关工具调用入口 URL、请求体和响应格式由 MCP 网关责任人提供。
- Agent 不直接调用 MCP Server，也不直接调用业务 API。
- Agent 不保存、不读取、不打印 Cookie、业务 Token 或密钥。
- 每次工具调用都必须经过 MCP 网关，由 MCP 网关向策略中心完成授权决策。

### 4.2 处理 MCP 网关返回的未授权

当 MCP 网关返回“未授权 + `toolId`”时，业务 Agent 执行：

1. 保存当前任务执行检查点。
2. 进入挂起状态，暂停目标工具调用。
3. 经 Agent 网关把 `tokenId + toolId` 授权请求传给业务后端。
4. 同时开始轮询策略中心授权状态。

只有策略中心运行时决策为：

```text
decision = AUTHORIZATION_REQUIRED
reason = USER_AUTHORIZATION_REQUIRED 或 PER_CALL_AUTHORIZATION_REQUIRED
```

才应该触发人在回路授权页面。其他拒绝原因都必须终止调用。

### 4.3 轮询授权状态(或者业务后端与Agent自行恢复状态)

挂起期间，业务 Agent 每 2 秒调用一次：

```http
POST /internal/conversation-authorizations/status
Content-Type: application/json
X-Trace-Id: trace-20260611-004

{
  "tokenId": "agent-a:user-42:conversation-99",
  "toolId": "crm.customer.delete"
}
```

未授权响应：

```json
{
  "status": "NOT_AUTHORIZED"
}
```

已授权响应：

```json
{
  "status": "AUTHORIZED"
}
```

处理要求：

- 最长轮询 1 分钟，超时后结束挂起任务，并经 Agent 网关返回用户未授权或授权超时。
- 查询到 `AUTHORIZED` 后，Agent 恢复检查点，但不能直接执行工具。
- 恢复后必须重新调用 MCP 网关，由 MCP 网关重新提交完整授权决策。
- 状态查询接口只检查当前对话授权记录，不替代 MCP 网关鉴权。

## 5. 正常调用路径

当前对话已经授权或工具无需授权时，调用路径如下：

```text
1. 用户向业务后端发起请求。
2. 业务后端向 Agent 网关提交 userId、conversationId、Agent 请求与 Cookie。
3. Agent 网关生成 tokenId，保存 Cookie，并把请求转发给 Agent。
4. Agent 携带 tokenId、toolId 和工具参数调用 MCP 网关。
5. MCP 网关向策略中心提交 tokenId + toolId。
6. 策略中心返回 ALLOW。
7. MCP 网关按 tokenId 向 Agent 网关获取 Cookie。
8. MCP 网关注入 Cookie 并调用 MCP Server。
9. MCP Server 调用业务 API。
10. 结果逐级返回给用户。
```

关键约束：

- 只有 `ALLOW` 可以驱动 MCP 网关获取 Cookie 和调用工具。
- Agent 收到的是脱敏后的工具结果，不应接触原始 Cookie。
- 全链路建议透传 `X-Trace-Id`，便于跨模块排查。

## 6. 未授权人在回路路径

目标工具绑定为 `USER_AUTH_REQUIRED` 且当前对话未授权，或工具绑定为 `PER_CALL_AUTH_REQUIRED` 且缺少一次性授权时，调用路径如下：

```text
1. Agent 携带 tokenId 调用 MCP 网关。
2. MCP 网关向策略中心提交 tokenId + toolId。
3. 策略中心返回 `AUTHORIZATION_REQUIRED + USER_AUTHORIZATION_REQUIRED` 或 `AUTHORIZATION_REQUIRED + PER_CALL_AUTHORIZATION_REQUIRED`。
4. MCP 网关向 Agent 返回未授权状态与 toolId。
5. Agent 保存检查点并挂起。
6. Agent 经 Agent 网关把 tokenId + toolId 授权请求传给业务后端。
7. 业务后端展示 1 分钟有效的授权页面。
8. 用户同意后，业务后端调用策略中心写入当前对话授权。
9. Agent 轮询查询到 AUTHORIZED。
10. Agent 恢复检查点，重新调用 MCP 网关。
11. MCP 网关重新鉴权，ALLOW 后继续工具调用。
```

如果 1 分钟内用户未同意，Agent 结束挂起任务，不写入授权，不调用 MCP 工具。

## 7. 失败处理与排障

| 场景 | 业务后端处理 | 业务 Agent 处理 |
| --- | --- | --- |
| MCP 网关返回未授权 | 等待 Agent 网关转发授权请求并展示授权页面 | 保存检查点，发起授权请求，开始轮询 |
| 策略中心返回 `DENY + TOOL_NOT_BOUND` | 不展示授权页面，提示工具未开通或联系管理员 | 终止工具调用 |
| 策略中心返回 `DENY + INVALID_TOKEN_ID` | 不展示授权页面，提示系统上下文异常 | 终止工具调用 |
| 策略中心返回 `DENY + POLICY_STORE_UNAVAILABLE` | 提示系统暂不可用 | 终止工具调用 |
| 策略中心返回 `DENY + AUTHORIZATION_STORE_UNAVAILABLE` | 提示系统暂不可用 | 终止工具调用 |
| 授权页面超过 1 分钟 | 不再提交授权确认 | 轮询超时后结束挂起任务 |
| 授权确认返回 `409 TOOL_NOT_BOUND` | 提示工具未绑定，不重试授权 | 继续等待直到超时或收到业务失败结果 |
| 授权确认返回 `409 AUTHORIZATION_NOT_REQUIRED` | 视为配置变化，提示重试当前任务 | 重新通过 MCP 网关发起工具调用 |
| 策略中心 HTTP `5xx` 或超时 | 不视为授权成功 | 终止或按业务重试策略重新发起任务 |

排障建议：

- 全链路传递同一个 `X-Trace-Id`。
- 排查工具是否已绑定到 Agent，先看策略中心管理配置。
- 排查 `AUTHORIZATION_REQUIRED` 后用户授权无效，重点看业务后端是否在 1 分钟内调用了授权确认接口。
- 排查 Agent 一直轮询未授权，重点看 `tokenId` 和 `toolId` 是否与授权确认请求完全一致。
- 排查 Cookie 相关问题，联系 Agent 网关和 MCP 网关责任人；策略中心不保存也不返回 Cookie。

## 8. 接入验收清单

业务后端接入完成需满足：

- 能向 Agent 网关提交 `userId + conversationId + Agent 请求 + Cookie`。
- 能接收 Agent 网关转发的 `tokenId + toolId` 授权请求。
- 能展示“允许本次对话调用该工具”的 1 分钟授权页面。
- 用户同意后，由服务端调用 `POST /internal/conversation-authorizations`。
- 对话结束或删除时调用清理接口。
- 浏览器页面不会直接调用策略中心。

业务 Agent 接入完成需满足：

- 能从 Agent 网关接收并透传 `tokenId`。
- 调用 MCP 工具时始终经过 MCP 网关。
- 收到未授权后保存检查点并挂起。
- 每 2 秒轮询授权状态，最长 1 分钟。
- 查询到 `AUTHORIZED` 后重新调用 MCP 网关，而不是直接执行工具。
- 超时、`DENY`、策略中心异常时均终止工具调用。
- 日志中不记录 Cookie、业务 Token、密码或密钥。

跨模块联调完成需满足：

- `NO_AUTH_REQUIRED` 工具无需弹授权页面即可调用成功。
- `USER_AUTH_REQUIRED` 工具首次调用触发授权页面。
- `PER_CALL_AUTH_REQUIRED` 工具每次调用触发授权页面，用户同意后仅放行下一次重试。
- 用户同意后，Agent 能恢复检查点并完成工具调用。
- 用户 1 分钟内未同意时，Agent 返回授权未完成。
- 未绑定工具直接拒绝，不进入授权页面。
- 对话结束清理后，同一 `tokenId + toolId` 再次检查不命中旧授权。

## 9. 相关文档

- [项目总体架构](../01-architecture/01-project-overall-architecture.md)
- [策略中心功能规格](../02-policy-center/01-policy-center-spec.md)
- [策略中心 API 契约](../02-policy-center/02-api-contract.md)
- [策略中心对外接口参考](../02-policy-center/08-external-api-reference.md)
- [策略中心验收场景](../02-policy-center/04-acceptance-scenarios.md)
