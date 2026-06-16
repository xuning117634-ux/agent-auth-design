# 开发人员详细接入文档
> 状态：V1 开发联调手册
> 适用对象：业务后端开发者、业务 Agent 开发者、联调负责人
> 最后更新：2026-06-16
> 阅读顺序：5-02
> 文档职责：在 [开发者接入用户旅程](01-developer-access-user-journey.md) 的基础上，按业务开发者接入旅程说明配置步骤、运行面调用、用户授权和联调验收。策略中心接口权威契约以 [策略中心 API 契约](../02-policy-center/02-api-contract.md) 为准。

## 1. 开发环境入口

| 系统 | 管理面地址 | 支持人员 |
| --- | --- | --- |
| MCP 网关管理面 | <https://console.his-beta.huawei.com/romaIoA> | 陈汉标 00642671；刘国斌 30025674 |
| Agent 网关管理面 | <https://console.his-beta.huawei.com/liveedadev> | 徐宁 00812671；雷昊毅 30047771 |
| 策略中心管理面 | <https://console.his-beta.huawei.com/liveedadev/#/policy> | 牛伟才 00771147；徐宁 00812671 |

运行面接口地址由各系统部署环境提供，本文使用以下占位变量：

```text
AGENT_GATEWAY_BASE_URL = Agent 网关运行面地址
MCP_GATEWAY_BASE_URL   = MCP 网关运行面地址
POLICY_CENTER_BASE_URL = 策略中心运行面地址
```


## 2. 接入前配置步骤

### 2.1 MCP 网关：工具登记并打开安全开关

业务方先在 MCP 网关登记 MCP 工具，并打开需要纳入安全治理的工具安全开关。

这一步完成后，MCP 网关会成为 Agent 调用工具的统一入口，后续工具调用由 MCP 网关负责向策略中心发起最终鉴权。

### 2.2 Agent 网关：发布 Agent 并绑定 MCP 工具

业务方在 Agent 网关管理面发布 Agent，并绑定计划使用的 MCP 工具。

配置完成后，业务后端通过 Agent 网关运行面接口 `/h2a/{agentId}/{userPath}` 触发目标 Agent。`agentId` 来自 Agent 发布结果，`userPath` 按目标 Agent 对外路径填写。

所有业务后端到 Agent 的流量都应先经过 Agent 网关，不能绕过 Agent 网关直连业务 Agent。

### 2.3 策略中心：配置工具标签和人员策略

业务方在策略中心管理面对已绑定工具配置：

```text
authMode:
  NO_AUTH_REQUIRED   = 无需用户授权
  USER_AUTH_REQUIRED = 需要用户授权

accessScope:
  PUBLIC     = 所有用户
  RESTRICTED = 仅指定用户
```

运行时决策顺序可以简单理解为：

```text
工具是否绑定 -> 人员策略 -> 是否授权
```

## 3. 业务后端调用 Agent 网关

### 3.1 H2A 入口

Agent 网关 H2A 入口用于把用户请求代理到目标 Agent，并完成 Cookie 隔离、`tokenId` 生成和 SSE 流式转发。

```http
POST /h2a/{agentId}/{userPath}
```

路径参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `agentId` | 是 | 目标 Agent 的唯一标识，对应 Agent 网关管理面发布的 Agent |
| `userPath` | 否 | 转发到 Agent 的路径，支持多级路径；缺省时转发到 Agent 根路径 |

请求头：

| Header | 必填 | 说明 |
| --- | --- | --- |
| `Cookie` | 是 | 用户认证 Cookie，只交给 Agent 网关隔离保存 |
| `sessionId` | 是 | 当前会话 ID，Agent 网关用它生成 `tokenId` |
| `userId` | 否 | 用户 ID；当前安全方案建议传递，用于策略中心人员策略和授权上下文 |
| `Content-Type` | 是 | 通常为 `application/json` |
| `Accept` | 否 | 建议为 `text/event-stream` |
| `X-Trace-Id` | 否 | 建议全链路透传，便于排障 |

请求地址与调试联系：雷昊毅 30047771

```http
POST {AGENT_GATEWAY_BASE_URL}/h2a/agent-a/chat
Content-Type: application/json
Accept: text/event-stream
Cookie: IDaaS_SSO=example-cookie
userId: user-42
sessionId: conversation-99
X-Trace-Id: trace-20260616-101

```

Agent 网关处理后会：

1. 根据 `agentId` 路由到目标 Agent。
2. 隔离保存 Cookie。
3. 按 `{agentId}:{userId}:{sessionId}` 生成 `tokenId`。
4. 写入 Redis，默认 TTL 为 1 小时。
5. 转发请求给业务 Agent。
6. 转发时移除原始 `Cookie` 头，并向 Agent 注入 `tokenId` 头。

成功响应：

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream
```

Agent 返回的 SSE 响应会由 Agent 网关原样流式回传给调用方。

典型错误：

| 场景 | 响应 |
| --- | --- |
| `agentId` 不存在或 Agent URL 为空 | HTTP `500`，SSE 错误事件 |
| 缺少 `sessionId` | Agent 网关直接关闭连接，无响应体 |

`agentId` 不存在时的错误事件示例：

```text
data: {"jsonrpc":"2.0","id":null,"error":{"code":-32001,"message":"Agent not found or URL is empty","data":{"agentId":"xxx"}}}
```

业务后端接入要求：

- 所有后端到 Agent 的请求都走 Agent 网关。
- `userId` 必须稳定标识当前登录用户。
- `sessionId` 在同一业务对话内保持不变。
- Cookie 只交给 Agent 网关，不传给业务 Agent。

## 4. 业务 Agent 调用 MCP 网关

业务 Agent 从 Agent 网关收到请求后，应读取 Agent 网关注入的 `tokenId` Header，并在调用 MCP 网关时原样透传。

MCP 网关工具调用入口、请求体和响应格式由 MCP 网关团队提供。通用安全要求如下：

- 调用 MCP 工具必须经过 MCP 网关。
- 调用 MCP 网关时必须携带 `tokenId`、目标工具标识和工具参数。
- 不直接调用 MCP Server 或业务 API。
- 不读取、不保存、不打印 Cookie。
- MCP 网关仍是最终工具调用鉴权点。

如果 MCP 网关返回“未授权 + `toolId`”，业务 Agent 应保存检查点并进入挂起状态，等待业务后端完成用户授权后再恢复。

## 5. 用户授权与策略中心接口

### 5.1 用户同意后写入当前对话授权

调用方：业务后端。
用途：用户在授权页面同意后，写入当前对话的单个工具授权。

```http
POST {POLICY_CENTER_BASE_URL}/internal/conversation-authorizations
Content-Type: application/json
X-Trace-Id: trace-20260616-301

{
  "tokenId": "agent-a:user-42:conversation-99",
  "toolId": "crm.customer.delete"
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

- 只能由业务后端服务端调用，浏览器页面不要直连策略中心。
- 授权页面和服务端授权会话最长有效 1 分钟。
- 重复调用同一个 `tokenId + toolId` 是幂等的。
- `409 TOOL_NOT_BOUND` 表示工具未绑定，不能继续授权。
- `409 AUTHORIZATION_NOT_REQUIRED` 表示工具无需授权，不应写入授权。
- `503` 或超时不得当作授权成功。

### 5.2 Agent 查询当前对话授权状态

调用方：挂起中的业务 Agent。
用途：用户授权页面打开后，Agent 查询当前对话授权是否已写入。

```http
POST {POLICY_CENTER_BASE_URL}/internal/conversation-authorizations/status
Content-Type: application/json
X-Trace-Id: trace-20260616-302

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

- Agent 每 2 秒查询一次，最长等待 1 分钟。
- 查询到 `AUTHORIZED` 后，Agent 恢复检查点。
- 恢复后必须重新调用 MCP 网关，不能直接执行 MCP 工具。
- 1 分钟内始终未授权时，Agent 按用户未授权结束任务。

### 5.3 对话结束时清理授权

调用方：业务后端。
用途：对话结束或删除时，清理该对话下全部工具授权。

如果业务后端已持有 `tokenId`：

```http
POST {POLICY_CENTER_BASE_URL}/internal/conversation-authorizations/cleanup
Content-Type: application/json
X-Trace-Id: trace-20260616-303

{
  "tokenId": "agent-a:user-42:conversation-99"
}
```

如果业务后端不希望理解 `tokenId` 拼接规则：

```http
POST {POLICY_CENTER_BASE_URL}/external/conversation-authorizations/cleanup
Content-Type: application/json
X-Trace-Id: trace-20260616-304

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

### 5.4 可选：判断用户是否可以访问 Agent

调用方：业务后端或业务 Agent。
用途：触发 Agent 前，判断当前用户是否可以访问目标 Agent。
是否必接：可选。如果业务入口已经通过其他方式完成 Agent 访问控制，可以不调用该接口。

```http
POST {POLICY_CENTER_BASE_URL}/internal/agent-access-decisions
Content-Type: application/json
X-Trace-Id: trace-20260616-305

{
  "agentId": "agent-a",
  "userId": "user-42"
}
```

允许响应：

```json
{
  "agentId": "agent-a",
  "userId": "user-42",
  "allowed": true,
  "reason": "AGENT_USER_WHITELISTED"
}
```

拒绝响应：

```json
{
  "agentId": "agent-a",
  "userId": "user-99",
  "allowed": false,
  "reason": "AGENT_USER_NOT_WHITELISTED"
}
```

处理要求：

- `allowed = false` 时，不要触发 Agent。
- HTTP 超时、`5xx` 或无法解析响应时按失败关闭处理。
- 本接口不替代 MCP 工具层面的授权决策。

### 5.5 可选：查询用户在 Agent 下可以访问的工具

调用方：业务后端或业务 Agent。
用途：展示或预过滤当前用户在某个 Agent 下可访问的工具。
是否必接：可选。适合业务侧需要提前展示可用工具列表或做前置过滤时使用。

```http
GET {POLICY_CENTER_BASE_URL}/internal/agents/agent-a/users/user-42/tools
X-Trace-Id: trace-20260616-306
```

成功响应：

```json
{
  "agentId": "agent-a",
  "userId": "user-42",
  "tools": [
    {
      "toolId": "crm.customer.query",
      "authMode": "NO_AUTH_REQUIRED"
    },
    {
      "toolId": "crm.customer.delete",
      "authMode": "USER_AUTH_REQUIRED"
    }
  ]
}
```

说明：

- 只返回当前 Agent 已绑定且当前用户可访问的工具。
- `authMode` 表示工具是否需要用户授权，不表示当前对话已经授权。
- 策略中心不返回工具名称和描述；展示信息需要与 MCP 工具目录合并。

## 6. 标准联调流程

### 6.1 无需授权工具

1. MCP 网关登记工具并打开安全开关。
2. Agent 网关发布 Agent，并绑定该工具。
3. 策略中心把该工具配置为 `NO_AUTH_REQUIRED`。
4. 业务后端通过 Agent 网关触发 Agent。
5. Agent 调用 MCP 网关。
6. MCP 网关向策略中心鉴权，得到 `ALLOW + NO_AUTH_REQUIRED`。
7. MCP 网关获取 Cookie 并调用 MCP Server。

验收结果：用户无需看到授权页面，工具调用成功。

### 6.2 需要用户授权工具

1. 策略中心把工具配置为 `USER_AUTH_REQUIRED`。
2. Agent 调用 MCP 网关。
3. MCP 网关鉴权得到 `AUTHORIZATION_REQUIRED`。
4. Agent 保存检查点并通知业务后端展示授权页面。
5. 用户同意后，业务后端调用 `POST /internal/conversation-authorizations`。
6. Agent 查询到授权成功后恢复检查点。
7. Agent 重新调用 MCP 网关。
8. MCP 网关再次鉴权得到 `ALLOW + CONVERSATION_AUTHORIZED`。

验收结果：首次调用被拦截，用户同意后任务恢复并完成。

### 6.3 拒绝和异常

以下情况都不能弹授权页，也不能继续调用 MCP 工具：

| 场景 | 处理 |
| --- | --- |
| 工具未绑定 | 直接拒绝，联系管理员完成绑定 |
| 用户不在 Agent 或 Tool 白名单 | 直接拒绝，提示无权限 |
| `tokenId` 非法 | 直接拒绝，排查 Agent 网关上下文 |
| 策略中心数据库不可用 | fail-closed，提示系统暂不可用 |
| Redis 不可用 | fail-closed，提示系统暂不可用 |
| 策略中心接口超时或 `5xx` | fail-closed，不得当作允许 |

## 7. 常见错误响应

```json
{
  "code": "INVALID_REQUEST",
  "message": "request is invalid",
  "traceId": "trace-20260616-400"
}
```

| HTTP | code | 常见原因 | 调用方处理 |
| ---: | --- | --- | --- |
| `400` | `INVALID_REQUEST` | 字段缺失、空白、重复、`tokenId` 格式非法 | 修正请求，不重试同样参数 |
| `409` | `TOOL_NOT_BOUND` | 工具未绑定当前 Agent | 不弹授权页，联系管理员配置 |
| `409` | `AUTHORIZATION_NOT_REQUIRED` | 工具为 `NO_AUTH_REQUIRED`，不应写授权 | 重新走 MCP 网关调用 |
| `503` | `POLICY_STORE_UNAVAILABLE` | MySQL 或策略库不可用 | fail-closed，使用 `traceId` 排障 |
| `503` | `AUTHORIZATION_STORE_UNAVAILABLE` | Redis 授权存储不可用 | fail-closed，使用 `traceId` 排障 |
| `500` | `INTERNAL_ERROR` | 未分类异常 | fail-closed，使用 `traceId` 排障 |

## 8. 接入验收清单

业务后端：

- 后端到 Agent 的所有流量都经过 Agent 网关。
- 能传递 `userId + sessionId + Cookie + Agent 请求`。
- 能接收 Agent 网关转发的授权请求并展示 1 分钟有效授权页面。
- 用户确认后由服务端调用策略中心授权确认接口。
- 对话结束或删除时调用清理接口。
- 浏览器页面不直接调用策略中心。

业务 Agent：

- 能从 Agent 网关接收并透传 `tokenId`。
- 调用工具始终经过 MCP 网关。
- 收到未授权后能保存检查点并挂起。
- 能按每 2 秒、最长 1 分钟查询授权状态，或通过业务后端与 Agent 自行恢复状态。
- 查询到授权成功后，重新经过 MCP 网关鉴权。
- 日志中不记录 Cookie、业务 Token、密码或密钥。

平台配置：

- MCP 工具已登记并打开安全开关。
- Agent 已发布并绑定目标 MCP 工具。
- 策略中心已配置工具授权标签和人员策略。
- `NO_AUTH_REQUIRED`、`USER_AUTH_REQUIRED`、未绑定、无权限、系统异常场景均已验证。

## 9. 相关文档

- [开发者接入用户旅程](01-developer-access-user-journey.md)
- [业务后端与业务 Agent 接入指南（参考材料）](99-reference-business-backend-agent-integration-guide.md)
- [策略中心 API 契约](../02-policy-center/02-api-contract.md)
- [策略中心对外接口参考](../02-policy-center/08-external-api-reference.md)
- [人员权限策略接口](../02-policy-center/09-user-policy-api.md)
- [Agent 网关 API 参考](../06-agent-gateway/02-agent-gateway-api-reference.md)
- [Agent 网关 H2A 代理设计](../06-agent-gateway/01-agent-gateway-project-overall-architecture.md)
