# 财经 Agent 授权预检与批量授权接口

> 状态：V1 扩展接口
> 适用对象：财经 Agent、业务后端
> 最后更新：2026-06-15
> 阅读顺序：5-03
> 说明：MCP 网关仍是最终工具调用鉴权点。本文件只描述财经 Agent 在调用 MCP 网关前的体验增强接口，以及业务后端批量写入当前对话授权记录的接口。

## 1. 接口总览

| 调用方 | 方法 | 路径 | 用途 |
| --- | --- | --- | --- |
| 财经 Agent | `POST` | `/internal/tool-authorization-prechecks` | 调用 MCP 网关前，批量判断哪些工具需要用户授权 |
| 业务后端 | `POST` | `/internal/conversation-authorizations/batch` | 用户确认后，批量写入当前对话工具授权 |

通用请求头：

```http
Content-Type: application/json
tokenid: agent-a:user-42:conversation-99
X-Trace-Id: trace-20260615-001
```

规则：

- `tokenid` 必传，格式仍为 `agentId:userId:conversationId`。
- `X-Trace-Id` 非必传；传了会透传，不传由策略中心生成。
- V1 暂不做接口认证，生产接入前需要通过内网、网关或后续认证机制限制调用方。
- Cookie、业务 Token、密钥不得传给策略中心。

## 2. 财经 Agent 工具授权预检

```http
POST /internal/tool-authorization-prechecks
tokenid: agent-a:user-42:conversation-99
Content-Type: application/json
```

请求：

```json
{
  "tools": [
    {
      "serverId": "finance-server",
      "toolName": "quoteQuery"
    },
    {
      "serverid": "finance-report-server",
      "toolname": "reportSearch"
    }
  ]
}
```

字段规则：

- `tools` 必须非空。
- 每个工具必须提供 `serverId` 和 `toolName`。
- 请求字段同时兼容 `serverId/serverid` 和 `toolName/toolname`。
- 策略中心先从 Header `tokenid` 解析出 `agentId`，再用 `agentId + serverId + toolName` 查询 `agent_policy_tool`。

目录查询规则：

```sql
SELECT agent_id, service_id, service_name, tool_name, tool_id
FROM agent_policy_tool
WHERE agent_id = :agentId
  AND service_id = :serverId
  AND tool_name = :toolName
  AND status = 1
ORDER BY id DESC
LIMIT 1;
```

说明：

- `agent_policy_tool` 只作为外部工具目录数据源，用来把 `serverId + toolName` 解析成 `serverName + toolId`。
- 它不替代现有 `agent_tool_policy`，也不影响 MCP 网关运行时授权决策。
- 本仓库不维护 `agent_policy_tool` 建表 SQL；本地联调需要调用方自行创建或准备该表。
- `service_name` 对应响应 `serverName`；为空时回退为 `service_id`。
- 任一输入工具查不到时，整个请求返回 `400 INVALID_REQUEST`。

策略判断规则：

- 对解析出的每个 `toolId` 复用现有授权决策逻辑。
- 只返回 `decision = AUTHORIZATION_REQUIRED` 的工具。
- 如果没有需要授权的工具，返回 HTTP `200`。
- 如果存在需要授权的工具，返回 HTTP `403`。
- 如果策略库或 Redis 不可用，返回 HTTP `503`，调用方不得继续执行 MCP 工具。

存在需要授权工具时：

```http
HTTP/1.1 403 Forbidden
```

```json
{
  "tokenid": "agent-a:user-42:conversation-99",
  "tools": [
    {
      "serverName": "财经服务",
      "toolName": "quoteQuery",
      "toolId": "finance.quote.query",
      "decision": "AUTHORIZATION_REQUIRED"
    }
  ]
}
```

没有需要授权工具时：

```http
HTTP/1.1 200 OK
```

```json
{
  "tokenid": "agent-a:user-42:conversation-99",
  "tools": []
}
```

错误示例：

```json
{
  "code": "INVALID_REQUEST",
  "message": "tool is not found in agent policy tool catalog",
  "traceId": "trace-20260615-001"
}
```

## 3. 当前对话批量授权确认

```http
POST /internal/conversation-authorizations/batch
tokenid: agent-a:user-42:conversation-99
Content-Type: application/json
```

请求：

```json
{
  "toolIds": [
    "finance.quote.query",
    "finance.report.search"
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
    "finance.quote.query",
    "finance.report.search"
  ]
}
```

规则：

- `toolIds` 必须非空。
- 每个 `toolId` 必须非空。
- 同一请求内 `toolId` 不得重复。
- 策略中心先完成整批校验，再写入 Redis。
- 任一工具未绑定当前 Agent、无需授权、`tokenid` 非法或策略库异常时，整批失败，不写入授权记录。
- `expiresInSeconds` 可选，表示授权记录相对有效期，单位秒；缺失时使用当前对话授权默认配置。
- 校验通过后写入 `authz:{tokenId}:{toolId}`。
- `USER_AUTH_REQUIRED` 工具在 TTL 内持续允许；`PER_CALL_AUTH_REQUIRED` 工具只允许下一次重试，命中后会被策略中心消费删除。
- 重复授权保持幂等。
- Redis 写入失败返回 `503 AUTHORIZATION_STORE_UNAVAILABLE`。

典型错误：

| HTTP | code | 场景 |
| ---: | --- | --- |
| `400` | `INVALID_REQUEST` | `tokenid` 非法、`toolIds` 为空、工具 ID 空白或重复 |
| `409` | `TOOL_NOT_BOUND` | 任一工具未绑定当前 Agent |
| `409` | `AUTHORIZATION_NOT_REQUIRED` | 任一工具为 `NO_AUTH_REQUIRED`，不应写入用户授权 |
| `503` | `POLICY_STORE_UNAVAILABLE` | 策略数据库不可用 |
| `503` | `AUTHORIZATION_STORE_UNAVAILABLE` | Redis 写入失败 |

## 4. 调用方处理建议

财经 Agent：

- 调用 MCP 网关前可先调用预检接口。
- 收到 `403` 时，将 `tokenid + tools` 透传给业务后端触发授权页面。
- 收到 `200` 时，可以继续调用 MCP 网关，但 MCP 网关仍会再次做最终鉴权。
- 收到 `400/409/503/5xx` 时，不得继续调用 MCP 工具。

业务后端：

- 用户确认授权后，调用批量授权确认接口。
- 授权页面应按工具标签表达授权含义：`USER_AUTH_REQUIRED` 可表达有效期内允许，`PER_CALL_AUTH_REQUIRED` 表达仅本次重试允许。
- 写入成功后通知 Agent 恢复检查点；Agent 必须重新经过 MCP 网关调用工具。

策略中心：

- 预检接口只改善授权体验，不替代 MCP 网关的最终运行时授权。
- 批量授权接口只写入当前对话授权，不提供跨对话或 Agent 级长期授权；相关能力暂不进入 V1 验收，未来演进中开发。
