# 未来授权能力演进

> 文档状态：未来设计候选，不属于当前版本实现范围，也不作为当前版本验收依据。
> 当前版本仍以 [项目总体架构](../project-overall-architecture.md) 中的单对话授权模型为准。

## 演进目标

未来可在不改变 MCP 网关与权限策略中心主要调用形式 `tokenId + toolId` 的前提下，逐步支持：

- 当前用户在当前 Agent 下跨对话授权 7 天。
- 当前用户在当前 Agent 下跨对话授权 30 天。
- 对话级和 Agent 级授权的立即撤销。
- 明文 tokenId 向不透明随机 tokenId 演进。
- 更完整的授权审计、授权列表与用户自助管理。

## 统一 Grant Hash 候选模型

跨对话授权不继续扩展多套顶层 Redis Key，而是使用一个 Hash 表达同一用户、Agent 和工具下的多种授权范围：

```text
grant:{userId}:{agentId}:{toolId}

agent                          -> expiresAt
conversation:{conversationId} -> ACTIVE
```

字段语义：

- `agent`：当前用户对当前 Agent 中该工具的跨对话授权，值为过期时间戳。
- `conversation:{conversationId}`：当前对话对该工具的授权，值为 `ACTIVE`。

授权检查：

1. 从 tokenId 获得 `userId`、`agentId` 和 `conversationId`。
2. 对 Grant Hash 执行：

   ```text
   HMGET agent conversation:{conversationId}
   ```

3. `agent` 字段存在且未过期，或当前 conversation 字段存在时，返回允许。
4. `agent` 字段已过期时惰性删除。
5. Redis 数据丢失、查询异常或两个字段均无效时默认拒绝。

## 授权范围

未来授权页面可提供：

| grantType | 用户含义 | Redis 写入 |
| --- | --- | --- |
| `CONVERSATION` | 仅本次对话 | `conversation:{conversationId} = ACTIVE` |
| `AGENT_7_DAYS` | 当前 Agent 7 天内不再询问该工具 | `agent = 7 天后的时间戳` |
| `AGENT_30_DAYS` | 当前 Agent 30 天内不再询问该工具 | `agent = 30 天后的时间戳` |

规则：

- 已有更长 Agent 授权时，新的较短授权不得缩短有效期。
- 撤销对话授权使用 `HDEL conversation:{conversationId}`。
- 撤销 Agent 授权使用 `HDEL agent`，下一次在线授权检查立即生效。
- Hash 为空时删除整个 Grant Key。
- 当前设计允许 Redis 数据丢失；丢失后默认未授权，用户需要重新确认。

## 不透明 tokenId 演进

当前明文 tokenId：

```text
tokenId = agentId:userId:conversationId
```

未来可替换为不可预测的随机 UUID，并由 Agent 网关主动向权限策略中心注册上下文：

```text
authctx:{tokenId} -> userId, agentId, conversationId
```

演进后的职责：

- Agent 网关为每个用户、Agent、对话创建并复用一个随机 tokenId。
- Agent 网关调用策略中心内部接口注册非敏感上下文。
- Cookie 仍只由 Agent 网关隔离保存，不写入策略中心。
- 策略中心通过 `authctx:{tokenId}` 解析授权上下文，再查询统一 Grant Hash。
- 删除 `authctx:{tokenId}` 可使对应上下文立即失效。

该演进不会改变 Agent、MCP 网关与策略中心之间的授权检查输入：

```text
tokenId + toolId
```

## 候选接口

```text
POST /internal/auth-contexts
{ tokenId, userId, agentId, conversationId }

POST /internal/tool-authorizations/check
{ tokenId, toolId }
-> { allowed }

POST /internal/tool-authorizations/grants
{ tokenId, toolId, grantType }

DELETE /internal/tool-authorizations/grants
{ tokenId, toolId, grantType }
```

这些接口仅用于描述未来边界，当前版本不要求实现。

## 迁移原则

- 优先保持 `tokenId + toolId` 检查协议稳定。
- 先引入统一 Grant Hash，再切换不透明 tokenId，避免一次迁移两个核心模型。
- 迁移期间不复用当前 `authz:{tokenId}:{toolId}` 数据；用户需要重新授权，避免权限放大。
- 上线前必须覆盖跨用户、跨 Agent、跨工具和跨对话隔离测试。
- 当前版本的单对话授权文档不得引用本文件中的未来模型作为已实现行为。
