# 权限策略中心 API 契约

> 状态：V1 草案，可作为接口实现基线
> 负责人：项目维护者
> 适用版本：V1
> 最后更新：2026-06-11
> 阅读顺序：02-02
> 依赖：功能语义以 [策略中心功能规格](01-policy-center-spec.md) 为准。
> 说明：V1 不增加 URL 版本前缀；成功响应按本文示例裸 JSON 返回，错误响应统一为 `{code,message,traceId}`。V1 暂不实现接口认证，生产接入前需要补充内部调用认证。

## 通用约定

- 请求和响应使用 `application/json`。
- `tokenId`、`agentId`、`toolId` 均区分大小写。
- 未知 JSON 字段按默认 JSON 反序列化策略忽略；调用方不得依赖未知字段产生行为。
- V1 暂不实现接口认证；内部接口和管理接口均假设调用方来自受信任网络或上游网关。
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
| `ALLOW` | `PER_CALL_AUTHORIZED` |
| `AUTHORIZATION_REQUIRED` | `USER_AUTHORIZATION_REQUIRED` |
| `AUTHORIZATION_REQUIRED` | `PER_CALL_AUTHORIZATION_REQUIRED` |
| `DENY` | `TOOL_NOT_BOUND` |
| `DENY` | `USER_TOOL_ACCESS_DENIED` |
| `DENY` | `INVALID_TOKEN_ID` |
| `DENY` | `POLICY_STORE_UNAVAILABLE` |
| `DENY` | `AUTHORIZATION_STORE_UNAVAILABLE` |

HTTP 语义：

- 请求 JSON 合法但授权结果为三态之一时返回 `200`。
- `tokenId` 格式非法属于授权结果，返回 `200 + DENY/INVALID_TOKEN_ID`。
- 字段缺失、空白或 JSON 非法返回 `400 + INVALID_REQUEST`。
- 未处理的服务错误返回 `500 + INTERNAL_ERROR`，调用方必须终止工具调用。
- 目标 Tool 未配置人员策略时按 `accessScope = PUBLIC`，跳过人员限制。
- 目标 Tool 为 `RESTRICTED` 且当前 userId 不在白名单时返回 `DENY + USER_TOOL_ACCESS_DENIED`。

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
    },
    {
      "toolId": "tool-c",
      "authMode": "PER_CALL_AUTH_REQUIRED",
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
    },
    {
      "toolId": "tool-c",
      "authMode": "PER_CALL_AUTH_REQUIRED"
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

V1 保存时不回调 MCP 网关校验工具目录，信任管理面前端提交的 `toolId` 来自 MCP 网关当前全量工具列表。

## 确认当前对话授权

```http
POST /internal/conversation-authorizations
```

调用方：业务后端。

请求：

```json
{
  "tokenId": "agent-a:user-42:conversation-99",
  "toolId": "tool-b",
  "expiresInSeconds": 3600
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
- 仅 `USER_AUTH_REQUIRED`、`PER_CALL_AUTH_REQUIRED` 或历史空标签的工具可以写入当前对话授权。
- `expiresInSeconds` 可选，表示本次授权记录的相对有效期，单位秒。
- `expiresInSeconds` 缺失时使用服务配置 `policy-center.authorization.ttl`。
- `expiresInSeconds` 必须大于 `0`，且不得超过 `policy-center.authorization.max-ttl`，否则返回 `400 + INVALID_REQUEST`。
- `USER_AUTH_REQUIRED` 的授权记录在 TTL 内持续有效，直到过期或对话清理。
- `PER_CALL_AUTH_REQUIRED` 的授权记录只允许下一次 MCP 网关鉴权放行；命中后策略中心会消费删除该记录，TTL 只表示用户确认后等待 Agent 重试的最长时间。
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

## 批量确认当前对话授权

```http
POST /internal/conversation-authorizations/batch
```

调用方：业务后端。

Header：

```http
X-AGW-ACCESS-TOKEN: agent-a:user-42:conversation-99
```

兼容规则：优先读取 `X-AGW-ACCESS-TOKEN`；如果缺失或为空，再读取旧 Header `tokenid`。

请求：

```json
{
  "toolIds": [
    "tool-b",
    "tool-c"
  ],
  "expiresInSeconds": 3600
}
```

成功响应：

```json
{
  "status": "AUTHORIZED",
  "tokenId": "agent-a:user-42:conversation-99",
  "toolCount": 2,
  "toolIds": [
    "tool-b",
    "tool-c"
  ]
}
```

规则：

- `toolIds` 必须非空，元素不能空白，且不能重复。
- `expiresInSeconds` 可选，语义与单工具授权确认一致。
- 整批先校验再写入；任一工具未绑定、无需授权、tokenId 非法或策略库异常时，整批失败，不写入授权。
- `USER_AUTH_REQUIRED` 写入普通当前对话授权记录。
- `PER_CALL_AUTH_REQUIRED` 写入一次性授权记录，下一次 MCP 网关鉴权命中后会被消费。
- Redis 写入失败返回 `503 + AUTHORIZATION_STORE_UNAVAILABLE`。

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

- 使用 `SCAN MATCH authz:{tokenId}:*` 分批删除 tokenId 对应的全部工具授权。
- tokenId 不存在或已经清理时仍返回成功，`deletedGrantCount` 为 `0`。
- 清理必须幂等，不使用 Redis `KEYS`。
- Redis 操作失败返回 `503 + AUTHORIZATION_STORE_UNAVAILABLE`。

## 外部清理当前对话授权

```http
POST /external/conversation-authorizations/cleanup
```

调用方：外部受信调用方、业务后端或未来第三方集成方。

请求：

```json
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

- 外部调用方不需要理解 `tokenId` 拼接规则。
- 策略中心内部构造 `tokenId = agentId:userId:conversationId` 后复用当前对话清理逻辑。
- `agentId`、`userId`、`conversationId` 必须非空，且均不能包含分隔符 `:`。
- 参数非法返回 `400 + INVALID_REQUEST`。
- Redis 操作失败返回 `503 + AUTHORIZATION_STORE_UNAVAILABLE`。
- V1 暂不实现接口认证；生产或第三方开放前必须接入认证或上游网关鉴权。

## 查询 Agent 当前人员策略

```http
GET /admin/agents/{agentId}/user-policies
```

调用方：策略中心管理端。

成功响应：

```json
{
  "agentId": "agent-a",
  "accessScope": "RESTRICTED",
  "agentUsers": [
    {
      "userId": "user-42",
      "updatedAt": "2026-06-10T10:00:00Z"
    }
  ],
  "tools": [
    {
      "toolId": "tool-a",
      "accessScope": "RESTRICTED",
      "users": [
        {
          "userId": "user-42",
          "updatedAt": "2026-06-10T10:00:00Z"
        }
      ]
    }
  ],
  "updatedAt": "2026-06-10T10:00:00Z"
}
```

规则：

- Agent 未配置人员策略时返回 `accessScope = PUBLIC` 和空白名单。
- `tools` 返回当前 Agent 的全部已绑定工具；未配置的 Tool 返回 `accessScope = PUBLIC` 和空白名单。
- 历史残留的未绑定工具人员策略不得返回给管理面。

## 管理员整份保存人员策略

```http
PUT /admin/agents/{agentId}/user-policies
```

调用方：策略中心管理端。

请求：

```json
{
  "accessScope": "RESTRICTED",
  "agentUsers": [
    {
      "userId": "user-42"
    }
  ],
  "tools": [
    {
      "toolId": "tool-a",
      "accessScope": "RESTRICTED",
      "users": [
        {
          "userId": "user-42"
        }
      ]
    }
  ]
}
```

成功响应：

```json
{
  "agentId": "agent-a",
  "agentUserRuleCount": 1,
  "toolUserRuleCount": 1,
  "updatedAt": "2026-06-10T10:00:00Z"
}
```

规则：

- 请求体表示该 Agent 保存后的完整人员策略，不是增量更新。
- Agent 和 Tool 的 `accessScope` 缺失或为 `null` 时按 `PUBLIC` 保存。
- `agentUsers: []` 表示清空 Agent 级用户规则。
- `tools: []` 表示全部 Tool 恢复 `PUBLIC` 并清空 Tool 白名单。
- 请求中未出现的 Tool 恢复为 `PUBLIC` 并清空其白名单。
- `PUBLIC` 状态允许保存白名单，但决策时忽略。
- `tools.toolId` 必须属于该 Agent 当前已绑定工具，否则返回 `409 + TOOL_NOT_BOUND`。
- `agentUsers[].userId` 和 `tools[].users[].userId` 既可填写单个工号，也可填写批量工号；批量值支持英文/中文逗号、英文/中文分号和换行分隔。
- 批量工号会自动去除首尾空白、过滤空项并按首次出现顺序去重；拆分后没有有效工号时返回 `400 + INVALID_REQUEST`。
- 同一请求中的重复工号自动去重，重复 `toolId` 返回 `400 + INVALID_REQUEST`。
- 保存操作在单个数据库事务内完成。

## 查询用户是否可访问 Agent

```http
POST /internal/agent-access-decisions
```

请求：

```json
{
  "agentId": "agent-a",
  "userId": "user-42"
}
```

成功响应：

```json
{
  "agentId": "agent-a",
  "userId": "user-42",
  "allowed": true,
  "reason": "AGENT_USER_WHITELISTED"
}
```

规则：

- 该接口供业务后端和业务 Agent 在触发 Agent 前调用。
- Agent 为 `PUBLIC` 时允许所有用户；为 `RESTRICTED` 时仅允许白名单用户。
- 该接口不参与工具调用授权决策。

## 查询用户可访问的 Agent

```http
GET /internal/users/{userId}/agents
```

成功响应：

```json
{
  "userId": "user-42",
  "agents": [
    {
      "agentId": "agent-a",
      "reason": "AGENT_USER_WHITELISTED"
    }
  ]
}
```

规则：

- 返回范围限定为策略中心已知 Agent。
- 策略中心已知 Agent 指存在工具策略或人员策略记录的 Agent。

## 查询用户可访问的工具

```http
GET /internal/agents/{agentId}/users/{userId}/tools
```

成功响应：

```json
{
  "agentId": "agent-a",
  "userId": "user-42",
  "tools": [
    {
      "serverName": "财经服务",
      "toolName": "查询客户",
      "toolId": "crm.customer.query",
      "authMode": "NO_AUTH_REQUIRED"
    }
  ]
}
```

规则：

- 只返回当前 Agent 已绑定且当前用户可访问的工具。
- `serverName` 来自 `agent_policy_tool.service_name`，为空时回退为 `service_id`。
- `toolName` 来自 `agent_policy_tool.tool_name`。
- `serverName` 和 `toolName` 仅用于展示，不参与授权判断；目录记录缺失时返回 `null`。
- 返回 PUBLIC Tool 和当前用户在白名单中的 RESTRICTED Tool。
- 该接口不检查用户是否可访问 Agent，也不检查 Redis 当前对话授权。

## 错误码

| code | HTTP | 含义 |
| --- | ---: | --- |
| `INVALID_REQUEST` | 400 | JSON、必填字段或枚举不合法 |
| `TOOL_NOT_BOUND` | 409 | 工具未绑定目标 Agent |
| `AUTHORIZATION_NOT_REQUIRED` | 409 | 工具无需用户授权，不应写入对话授权 |
| `POLICY_STORE_UNAVAILABLE` | 503 | 策略配置数据库不可用 |
| `AUTHORIZATION_STORE_UNAVAILABLE` | 503 | 当前对话授权 Redis 不可用 |
| `INTERNAL_ERROR` | 500 | 未分类的服务内部错误 |

## 已确认契约

- HTTP URL 不增加 `/v1` 或服务名前缀。
- V1 暂不实现接口认证、授权或调用方身份校验。
- 成功响应不使用统一外层包裹。
- 用户确认请求不增加 `authorizationRequestId` 或签名证明。
- 分布式追踪 ID 优先使用 `X-Trace-Id` 请求头；缺失时由策略中心生成并写入错误响应和日志。
