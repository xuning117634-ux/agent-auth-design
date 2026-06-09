# 权限策略中心数据模型

> 状态：V1 逻辑模型
> 负责人：项目维护者
> 适用版本：V1
> 最后更新：2026-06-09
> 阅读顺序：02-03
> 文档职责：数据库与 Redis 结构的唯一事实来源。数据库产品、字段长度和迁移工具尚未确定。

## 关系数据库

### agent_tool_policy

记录存在表示工具已绑定到 Agent；记录不存在表示该 Agent 未绑定该工具。

该表只存管理员选择绑定的工具，不保存 MCP 网关的全量工具目录。

```text
agent_tool_policy
- agent_id
- tool_id
- auth_mode: NO_AUTH_REQUIRED | USER_AUTH_REQUIRED
- created_at
- updated_at
- UNIQUE(agent_id, tool_id)
```

逻辑约束：

- `agent_id`、`tool_id`、`auth_mode`、`created_at`、`updated_at` 均应为非空。
- `auth_mode` 新写入时只允许两个枚举值。
- 为兼容历史数据，运行时读取到空 `auth_mode` 时按 `USER_AUTH_REQUIRED` 处理。
- `(agent_id, tool_id)` 唯一约束保证不同 Agent 和工具配置相互隔离。
- 建议以 `agent_id` 建立查询索引，支持整份读取和整份覆盖。
- 不为全量 MCP 工具建表，不复制工具名称、描述、状态或服务器信息。
- 工具详情、工具状态和当前工具全集始终由 MCP 网关提供。

物理字段类型、字符串长度、时间精度、数据库引擎及 DDL 为 `TBD`。

### 整份覆盖事务

`PUT /admin/agents/{agentId}/tool-policies` 在单个事务中完成：

1. 校验请求内工具不重复，空标签归一化为 `USER_AUTH_REQUIRED`。
2. 新增不存在的 `(agent_id, tool_id)`。
3. 更新已存在记录的 `auth_mode` 和 `updated_at`。
4. 删除该 Agent 下未出现在请求中的旧记录。
5. 提交事务后才返回成功。

请求内工具表示用户从 MCP 当前全量工具列表中选择绑定的部分工具。事务失败必须整体回滚。并发写入采用最后成功提交事务的完整列表作为最终状态。

## Redis 当前对话授权

### 授权记录

```text
Key:   authz:{tokenId}:{toolId}
Value: 1
```

语义：

- Key 存在表示该 tokenId 对应的当前对话已授权调用该工具。
- Key 不存在表示尚未授权。
- `{tokenId}` 同时作为 Redis Cluster hash tag，使同一对话的授权记录落入同一 slot。
- Value 不承载用户、Agent、过期时间或授权范围信息。

### 清理索引

为避免对话结束时使用 Redis `KEYS`：

```text
Key:    authz-index:{tokenId}
Type:   Set
Member: toolId
```

确认授权时：

1. 执行 `SET authz:{tokenId}:{toolId} 1`。
2. 执行 `SADD authz-index:{tokenId} {toolId}`。

清理对话时：

1. 读取 `authz-index:{tokenId}` 的全部 toolId。
2. 删除对应的 `authz:{tokenId}:{toolId}`。
3. 删除 `authz-index:{tokenId}`。

授权记录和索引写入必须作为一个原子操作实现；具体使用事务、Lua 脚本或客户端原子批处理取决于 Redis 客户端选型。

### 生命周期与 TTL

- 业务生命周期：从用户确认授权开始，到业务后端通知对话结束或删除。
- 用户不能为当前授权选择 7 天、30 天或其他期限。
- 对话清理是主删除机制。
- 为防止业务后端漏发清理造成孤儿数据，物理安全 TTL 的具体时长为 `TBD`，确定后授权 Key 和索引 Key 必须使用一致的到期策略。

## 并发与幂等

- 重复授权确认：`SET` 和 `SADD` 后的最终状态不变，接口幂等。
- 重复对话清理：不存在 Key 时视为成功，接口幂等。
- 授权查询与清理并发：清理完成后的查询必须返回未授权。
- 清理与迟到授权确认并发：当前缺少“对话已关闭”状态，最终仲裁方式为 `TBD`；实现确认接口前必须解决。
- 管理员策略更新与运行时查询并发：每次查询读取一个已提交状态，不读取未提交事务。

## 失败语义

| 存储故障 | 运行时行为 |
| --- | --- |
| 策略数据库查询失败 | `DENY + POLICY_STORE_UNAVAILABLE` |
| Redis 查询失败 | `DENY + AUTHORIZATION_STORE_UNAVAILABLE` |
| Redis 授权写入失败 | 确认接口失败，不得向用户报告授权成功 |
| Redis 清理失败 | 清理接口失败并告警，不得静默忽略 |
| Redis 数据丢失 | 视为未授权，用户重新确认 |

数据库是 Agent-工具策略的当前真相源。V1 不为该表增加 Redis 查询缓存。

## 数据隔离

必须通过自动化测试证明：

- 不同 `agentId` 不共享工具绑定。
- 不同 `userId` 不共享当前对话授权。
- 不同 `conversationId` 不共享当前对话授权。
- 不同 `toolId` 不共享当前对话授权。

## 待确认项

- 关系数据库产品、字符集和迁移工具。
- ID 字段最大长度及时间字段类型。
- Redis 部署模式和客户端。
- 物理安全 TTL。
- 清理与迟到授权确认的并发仲裁模型。
