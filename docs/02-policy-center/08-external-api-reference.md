# 策略中心对外接口参考

> 状态：V1 当前实现  
> 适用对象：MCP 网关、管理面前端、业务后端、Agent 开发人员  
> 服务地址示例：`http://localhost:18080`  
> 最后更新：2026-06-10  
> 权威契约：[策略中心 API 契约](02-api-contract.md)

本文集中列出策略中心当前对外提供的全部业务接口及示例数据，供调用方联调使用。接口字段和错误语义以当前代码及 [API 契约](02-api-contract.md) 为准。

## 1. 接口总览

| 编号 | 调用方 | 方法 | 路径 | 用途 |
| --- | --- | --- | --- | --- |
| 1 | MCP 网关 | `POST` | `/internal/authorization-decisions` | 获取工具调用授权决策 |
| 2 | 管理面前端 | `GET` | `/admin/agents/{agentId}/tool-policies` | 查询 Agent 已绑定的工具策略 |
| 3 | 管理面前端 | `PUT` | `/admin/agents/{agentId}/tool-policies` | 整份保存 Agent 工具策略 |
| 4 | 业务后端 | `POST` | `/internal/conversation-authorizations` | 确认当前对话工具授权 |
| 5 | Agent | `POST` | `/internal/conversation-authorizations/status` | 轮询当前对话授权状态 |
| 6 | 业务后端 | `POST` | `/internal/conversation-authorizations/cleanup` | 清理当前对话全部工具授权 |
| 7 | 外部受信调用方 | `POST` | `/external/conversation-authorizations/cleanup` | 按 agentId、userId、conversationId 清理当前对话全部工具授权 |

V1 暂未实现接口认证。生产接入前必须由内部网络、上游网关或后续认证机制限制调用方，尤其不能将 `/internal/**` 直接暴露到公网。

## 2. 通用约定

### 2.1 请求头

```http
Content-Type: application/json
X-Trace-Id: trace-20260609-001
```

- `X-Trace-Id` 建议由调用方生成并全链路透传。
- 未传递 `X-Trace-Id` 时，策略中心会自动生成 UUID。
- 策略中心会在响应头回写最终使用的 `X-Trace-Id`。
- 控制台日志会输出 `traceId`，并记录 `HTTP_REQUEST_START`、`HTTP_REQUEST_END`、授权决策和授权确认等核心事件。
- 策略库、Redis 等底层存储异常会输出 WARN 日志和原始异常堆栈；授权决策 fail-closed 会额外输出 `AUTHORIZATION_DECISION_FAIL_CLOSED`。
- 普通业务拒绝或轮询未授权不会额外输出异常日志，避免日志过于频繁。
- 成功响应直接返回业务 JSON，不使用统一外层包装。

### 2.2 tokenId 格式

```text
tokenId = agentId:userId:conversationId
```

示例：

```text
agent-a:user-42:conversation-99
```

三个字段必须非空，且字段内部不能包含分隔符 `:`。

### 2.3 通用错误响应

```json
{
  "code": "INVALID_REQUEST",
  "message": "request is invalid",
  "traceId": "trace-20260609-001"
}
```

| HTTP | code | 含义 |
| ---: | --- | --- |
| `400` | `INVALID_REQUEST` | 请求体、必填字段、枚举或 tokenId 格式非法 |
| `409` | `TOOL_NOT_BOUND` | 工具未绑定目标 Agent |
| `409` | `AUTHORIZATION_NOT_REQUIRED` | 工具无需用户授权，不应写入对话授权 |
| `503` | `POLICY_STORE_UNAVAILABLE` | 工具策略数据库不可用 |
| `503` | `AUTHORIZATION_STORE_UNAVAILABLE` | 对话授权 Redis 不可用 |
| `500` | `INTERNAL_ERROR` | 未分类的服务内部错误 |

任何 `5xx` 都必须按失败关闭处理，调用方不得继续执行 MCP 工具。

## 3. 获取工具调用授权决策

**调用方：** MCP 网关

```http
POST /internal/authorization-decisions
```

请求示例：

```http
POST http://localhost:18080/internal/authorization-decisions
Content-Type: application/json
X-Trace-Id: trace-20260609-001

{
  "tokenId": "agent-a:user-42:conversation-99",
  "toolId": "crm.customer.query"
}
```

### 3.1 无需用户授权

```json
{
  "decision": "ALLOW",
  "reason": "NO_AUTH_REQUIRED"
}
```

### 3.2 当前对话已经授权

```json
{
  "decision": "ALLOW",
  "reason": "CONVERSATION_AUTHORIZED"
}
```

### 3.3 需要用户授权

```json
{
  "decision": "AUTHORIZATION_REQUIRED",
  "reason": "USER_AUTHORIZATION_REQUIRED"
}
```

MCP 网关必须停止工具调用并向 Agent 返回“未授权 + `toolId`”，不得获取 Cookie 或调用 MCP Server。

### 3.4 工具未绑定

```json
{
  "decision": "DENY",
  "reason": "TOOL_NOT_BOUND"
}
```

该结果不能进入人在回路授权流程。

### 3.5 tokenId 非法

```json
{
  "decision": "DENY",
  "reason": "INVALID_TOKEN_ID"
}
```

### 3.6 存储不可用

策略数据库异常：

```json
{
  "decision": "DENY",
  "reason": "POLICY_STORE_UNAVAILABLE"
}
```

Redis 异常：

```json
{
  "decision": "DENY",
  "reason": "AUTHORIZATION_STORE_UNAVAILABLE"
}
```

以上业务决策均返回 HTTP `200`。只有请求字段缺失、空白或 JSON 非法时返回 `400 INVALID_REQUEST`。

合法的决策组合：

| decision | reason |
| --- | --- |
| `ALLOW` | `NO_AUTH_REQUIRED` |
| `ALLOW` | `CONVERSATION_AUTHORIZED` |
| `AUTHORIZATION_REQUIRED` | `USER_AUTHORIZATION_REQUIRED` |
| `DENY` | `TOOL_NOT_BOUND` |
| `DENY` | `INVALID_TOKEN_ID` |
| `DENY` | `POLICY_STORE_UNAVAILABLE` |
| `DENY` | `AUTHORIZATION_STORE_UNAVAILABLE` |

## 4. 查询 Agent 工具策略

**调用方：** 管理面前端

```http
GET /admin/agents/{agentId}/tool-policies
```

请求示例：

```http
GET http://localhost:18080/admin/agents/agent-a/tool-policies
X-Trace-Id: trace-20260609-002
```

成功响应：

```json
{
  "agentId": "agent-a",
  "tools": [
    {
      "toolId": "crm.customer.query",
      "authMode": "NO_AUTH_REQUIRED",
      "updatedAt": "2026-06-09T10:00:00Z"
    },
    {
      "toolId": "crm.customer.delete",
      "authMode": "USER_AUTH_REQUIRED",
      "updatedAt": "2026-06-09T10:05:00Z"
    }
  ],
  "updatedAt": "2026-06-09T10:05:00Z"
}
```

没有绑定工具时：

```json
{
  "agentId": "agent-a",
  "tools": [],
  "updatedAt": null
}
```

规则：

- 只返回策略中心保存的已绑定工具。
- 未出现在 `tools` 中的工具视为未绑定。
- 工具名称、描述和状态由 MCP 网关的全量工具列表提供，策略中心不复制工具目录。
- 历史空 `authMode` 返回时归一化为 `USER_AUTH_REQUIRED`。

## 5. 整份保存 Agent 工具策略

**调用方：** 管理面前端

```http
PUT /admin/agents/{agentId}/tool-policies
```

请求示例：

```http
PUT http://localhost:18080/admin/agents/agent-a/tool-policies
Content-Type: application/json
X-Trace-Id: trace-20260609-003

{
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

成功响应：

```json
{
  "agentId": "agent-a",
  "toolCount": 2,
  "updatedAt": "2026-06-09T10:10:00Z"
}
```

解除该 Agent 的全部工具绑定：

```json
{
  "tools": []
}
```

规则：

- 请求体表示该 Agent 保存后的完整绑定结果，不是增量更新。
- 未包含在本次 `tools` 中的旧绑定会被删除。
- 同一请求中的 `toolId` 不能重复。
- `authMode` 缺失或为 `null` 时按 `USER_AUTH_REQUIRED` 保存。
- 当前合法枚举只有 `NO_AUTH_REQUIRED`、`USER_AUTH_REQUIRED`。
- 保存操作在单个数据库事务内完成。

重复 `toolId` 错误示例：

```json
{
  "code": "INVALID_REQUEST",
  "message": "duplicate toolId: crm.customer.query",
  "traceId": "trace-20260609-003"
}
```

## 6. 确认当前对话授权

**调用方：** 业务后端

用户在授权页面明确同意后，业务后端调用此接口。该接口不能由浏览器页面直接调用。

```http
POST /internal/conversation-authorizations
```

请求示例：

```http
POST http://localhost:18080/internal/conversation-authorizations
Content-Type: application/json
X-Trace-Id: trace-20260609-004

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

规则：

- 只有已绑定且标签为 `USER_AUTH_REQUIRED` 的工具可以写入授权。
- 重复调用是幂等的，仍返回 `AUTHORIZED`。
- 当前实现写入 Redis Key：`authz:{tokenId}:{toolId}`。
- 当前物理安全 TTL 为 7 天；业务后端仍必须在对话结束时调用清理接口。
- 用户确认真实性及授权页面 1 分钟有效期由受信任业务后端保证。

工具未绑定：

```json
{
  "code": "TOOL_NOT_BOUND",
  "message": "tool is not bound",
  "traceId": "trace-20260609-004"
}
```

工具无需授权：

```json
{
  "code": "AUTHORIZATION_NOT_REQUIRED",
  "message": "authorization is not required",
  "traceId": "trace-20260609-004"
}
```

以上两种错误均返回 HTTP `409`。

## 7. 查询当前对话授权状态

**调用方：** 挂起中的 Agent

```http
POST /internal/conversation-authorizations/status
```

请求示例：

```http
POST http://localhost:18080/internal/conversation-authorizations/status
Content-Type: application/json
X-Trace-Id: trace-20260609-005

{
  "tokenId": "agent-a:user-42:conversation-99",
  "toolId": "crm.customer.delete"
}
```

已授权：

```json
{
  "status": "AUTHORIZED"
}
```

未授权：

```json
{
  "status": "NOT_AUTHORIZED"
}
```

规则：

- Agent 每 2 秒查询一次，最长等待 1 分钟。
- 查询到 `AUTHORIZED` 后，Agent 恢复检查点并重新调用 MCP 网关。
- 本接口只检查 Redis 授权记录，不替代 MCP 网关发起的完整授权决策。
- 1 分钟内始终为 `NOT_AUTHORIZED` 时，Agent 按用户未授权结束任务。

## 8. 清理当前对话授权

**调用方：** 业务后端

对话结束或删除时调用，删除该 `tokenId` 下全部工具授权。

```http
POST /internal/conversation-authorizations/cleanup
```

请求示例：

```http
POST http://localhost:18080/internal/conversation-authorizations/cleanup
Content-Type: application/json
X-Trace-Id: trace-20260609-006

{
  "tokenId": "agent-a:user-42:conversation-99"
}
```

成功响应：

```json
{
  "status": "CLEARED",
  "deletedGrantCount": 2
}
```

没有可清理记录时：

```json
{
  "status": "CLEARED",
  "deletedGrantCount": 0
}
```

规则：

- 清理接口幂等，可安全重复调用。
- 清理范围是该 `tokenId` 对应的全部工具授权。
- Redis 异常返回 HTTP `503 + AUTHORIZATION_STORE_UNAVAILABLE`。

## 9. 外部清理当前对话授权

**调用方：** 外部受信调用方、业务后端或未来第三方集成方

调用方不需要理解策略中心内部 `tokenId` 拼接规则，只需传递三个业务字段。策略中心内部会构造 canonical `tokenId = agentId:userId:conversationId`，并复用当前对话授权清理逻辑。

```http
POST /external/conversation-authorizations/cleanup
```

请求示例：

```http
POST http://localhost:18080/external/conversation-authorizations/cleanup
Content-Type: application/json
X-Trace-Id: trace-20260610-001

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

规则：

- `agentId`、`userId`、`conversationId` 必须非空。
- 三个字段都不能包含分隔符 `:`，否则返回 HTTP `400 + INVALID_REQUEST`。
- 清理范围与内部 cleanup 一致，删除 `authz:{agentId:userId:conversationId}:*`。
- V1 暂不做接口认证；生产或第三方开放前必须接入认证或上游网关鉴权。

## 10. 调用方处理要求

### MCP 网关

- 只在 `ALLOW` 时继续获取 Cookie 并调用 MCP Server。
- `AUTHORIZATION_REQUIRED` 返回 Agent 未授权状态及原始 `toolId`。
- `DENY`、HTTP `5xx`、超时或无法解析响应时一律终止工具调用。

### Agent

- 收到未授权状态后保存检查点并挂起。
- 轮询状态成功后必须重新经过 MCP 网关鉴权，不能直接恢复工具执行。
- 轮询超过 1 分钟后结束任务。

### 业务后端

- 只有用户明确同意后才能调用授权确认接口。
- 授权页面和服务端授权会话最长有效 1 分钟。
- 对话结束或删除时调用清理接口。

### 管理面前端

- 从 Agent 网关获取管理员可管理的 Agent。
- 从 MCP 网关获取当前全量工具列表。
- 只向策略中心提交用户实际选择绑定的工具。
- 保存成功后重新查询策略，刷新页面状态。

## 11. 健康检查

健康检查不属于业务接口，但可用于部署和本地联调：

```http
GET /actuator/health
```

正常响应：

```json
{
  "status": "UP"
}
```

本地启动服务后，可执行以下脚本完成全部接口的端到端验收：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\verify-policy-center.ps1
```
