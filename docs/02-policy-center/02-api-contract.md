# 权限策略中心 API 契约

> 状态：V1 草案，可作为接口实现基线
> 负责人：项目维护者
> 适用版本：V1
> 最后更新：2026-06-09
> 阅读顺序：02-02
> 依赖：功能语义以 [策略中心功能规格](01-policy-center-spec.md) 为准。
> 说明：HTTP 鉴权方式、统一响应包裹和最终 URL 前缀为 `TBD`；本文件中的业务字段和语义已固定。

## 通用约定

- 请求和响应使用 `application/json`。
- `tokenId`、`agentId`、`toolId` 均区分大小写。
- 未知 JSON 字段是否拒绝目前为 `TBD`。
- 内部接口只允许已认证的内部服务调用；具体认证方式为 `TBD`。
- HTTP `5xx` 表示策略中心自身无法完成操作，不得被调用方解释为允许。

通用错误响应：

```json
{
  "code": "INVALID_REQUEST",
  "message": "toolId must not be blank",
  "traceId": "01J..."
}
```

## 运行时授权决策

```http
POST /internal/authorization-decisions
```

调用方：MCP 网关。

请求：

```json
{
  "tokenId": "agent-a:user-42:conversation-99",
  "toolId": "tool-a"
}
```

允许响应：

```json
{
  "decision": "ALLOW",
  "reason": "CONVERSATION_AUTHORIZED"
}
```

需要用户授权：

```json
{
  "decision": "AUTHORIZATION_REQUIRED",
  "reason": "USER_AUTHORIZATION_REQUIRED"
}
```

拒绝响应：

```json
{
  "decision": "DENY",
  "reason": "TOOL_NOT_BOUND"
}
```

决策与原因的合法组合：

| decision | reason |
| --- | --- |
| `ALLOW` | `NO_AUTH_REQUIRED` |
| `ALLOW` | `CONVERSATION_AUTHORIZED` |
| `AUTHORIZATION_REQUIRED` | `USER_AUTHORIZATION_REQUIRED` |
| `DENY` | `TOOL_NOT_BOUND` |
| `DENY` | `INVALID_TOKEN_ID` |
| `DENY` | `POLICY_STORE_UNAVAILABLE` |
| `DENY` | `AUTHORIZATION_STORE_UNAVAILABLE` |

HTTP 语义：

- 请求 JSON 合法但授权结果为三态之一时返回 `200`。
- `tokenId` 格式非法属于授权结果，返回 `200 + DENY/INVALID_TOKEN_ID`。
- 字段缺失、空白或 JSON 非法返回 `400 + INVALID_REQUEST`。
- 未处理的服务错误返回 `500 + INTERNAL_ERROR`，调用方必须终止工具调用。

## 查询 Agent 当前工具策略

```http
GET /admin/agents/{agentId}/tool-policies
```

调用方：策略中心管理端。

成功响应：

```json
{
  "agentId": "agent-a",
  "tools": [
    {
      "toolId": "tool-a",
      "authMode": "NO_AUTH_REQUIRED",
      "updatedAt": "2026-06-09T10:00:00Z"
    },
    {
      "toolId": "tool-b",
      "authMode": "USER_AUTH_REQUIRED",
      "updatedAt": "2026-06-09T10:00:00Z"
    }
  ],
  "updatedAt": "2026-06-09T10:00:00Z"
}
```

规则：

- 只返回该 Agent 已绑定的工具策略。
- 未出现在 `tools` 中的工具视为未绑定。
- 后端返回历史空 `authMode` 时必须归一化为 `USER_AUTH_REQUIRED`。
- 策略中心不返回 MCP 工具详情；工具名称、描述、状态和全集由 MCP 网关提供。

## 管理员整份保存工具策略

```http
PUT /admin/agents/{agentId}/tool-policies
```

调用方：策略中心管理端。

请求：

```json
{
  "tools": [
    {
      "toolId": "tool-a",
      "authMode": "NO_AUTH_REQUIRED"
    },
    {
      "toolId": "tool-b",
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
  "updatedAt": "2026-06-09T10:00:00Z"
}
```

规则：

- 请求体表示该 Agent 保存后的完整已绑定工具策略。
- `tools` 只包含用户从 MCP 当前全量工具列表中选择绑定的工具。
- `tools: []` 表示解绑该 Agent 的全部工具。
- 请求中未包含的旧绑定会被删除；未包含工具运行时视为 `TOOL_NOT_BOUND`。
- 同一请求中 `toolId` 不得重复。
- `authMode` 缺失或为 `null` 时按 `USER_AUTH_REQUIRED` 保存。
- 非法枚举、空 `agentId`、空 `toolId` 或重复 `toolId` 返回 `400`。
- 新增、更新和删除必须在一个数据库事务中完成。
- 同一请求重复提交必须得到相同最终状态。
- 并发整份保存采用“最后成功提交的事务生效”。

工具是否仍存在于 MCP 网关工具目录中的服务端校验方式为 `TBD`。

## 确认当前对话授权

```http
POST /internal/conversation-authorizations
```

调用方：业务后端。

请求：

```json
{
  "tokenId": "agent-a:user-42:conversation-99",
  "toolId": "tool-b"
}
```

成功响应：

```json
{
  "status": "AUTHORIZED",
  "tokenId": "agent-a:user-42:conversation-99",
  "toolId": "tool-b"
}
```

规则：

- 写入前重新解析 tokenId，并检查工具仍绑定当前 Agent。
- 仅 `USER_AUTH_REQUIRED` 或历史空标签的工具可以写入当前对话授权。
- 重复确认必须幂等返回 `AUTHORIZED`。
- 工具未绑定返回 `409 + TOOL_NOT_BOUND`。
- 工具为 `NO_AUTH_REQUIRED` 时返回 `409 + AUTHORIZATION_NOT_REQUIRED`。
- Redis 写入失败返回 `503 + AUTHORIZATION_STORE_UNAVAILABLE`。
- 用户确认真实性和 1 分钟服务端会话有效性由受信任业务后端保证；未来若改为策略中心校验，需要扩展请求契约。

## 查询当前对话授权状态

```http
POST /internal/conversation-authorizations/status
```

调用方：挂起中的 Agent。

请求：

```json
{
  "tokenId": "agent-a:user-42:conversation-99",
  "toolId": "tool-b"
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

规则：

- 该接口只查询当前对话授权记录，不替代 MCP 网关发起的完整授权决策。
- Agent 每 2 秒调用一次，最多持续 1 分钟。
- Redis 查询失败返回 `503 + AUTHORIZATION_STORE_UNAVAILABLE`。
- Agent 查询到 `AUTHORIZED` 后仍必须重新通过 MCP 网关调用工具。

## 清理当前对话授权

```http
POST /internal/conversation-authorizations/cleanup
```

调用方：业务后端。

请求：

```json
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

规则：

- 删除 tokenId 对应的全部工具授权及清理索引。
- tokenId 不存在或已经清理时仍返回成功，`deletedGrantCount` 为 `0`。
- 清理必须幂等，不使用 Redis `KEYS` 扫描。
- Redis 操作失败返回 `503 + AUTHORIZATION_STORE_UNAVAILABLE`。

## 错误码

| code | HTTP | 含义 |
| --- | ---: | --- |
| `INVALID_REQUEST` | 400 | JSON、必填字段或枚举不合法 |
| `TOOL_NOT_BOUND` | 409 | 工具未绑定目标 Agent |
| `AUTHORIZATION_NOT_REQUIRED` | 409 | 工具无需用户授权，不应写入对话授权 |
| `POLICY_STORE_UNAVAILABLE` | 503 | 策略配置数据库不可用 |
| `AUTHORIZATION_STORE_UNAVAILABLE` | 503 | 当前对话授权 Redis 不可用 |
| `INTERNAL_ERROR` | 500 | 未分类的服务内部错误 |

## 待确认契约

- HTTP URL 是否增加版本前缀，例如 `/v1`。
- 管理接口和内部接口的认证、授权及调用方身份字段。
- 是否使用统一响应包裹结构。
- 用户确认是否增加 `authorizationRequestId` 或签名证明。
- 分布式追踪 ID 使用请求头还是请求字段传递。
