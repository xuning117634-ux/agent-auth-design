# 人的权限策略配置方案

> 状态：V1 当前实现
> 适用版本：V1
> 最后更新：2026-06-15
> 阅读顺序：02-07

人员策略的管理面与业务接口调用说明参见 [人员权限策略接口](09-user-policy-api.md)。

## 背景与边界

策略中心支持管理员从人的维度控制 Agent 和 Tool 的访问范围：

- Agent 访问策略供业务后端和业务 Agent 在触发 Agent 前使用。
- Tool 用户策略供策略中心运行时工具决策和业务查询接口使用。
- Agent 访问策略不参与 `/internal/authorization-decisions`。
- Tool 用户策略只能配置在当前 Agent 已绑定的工具上。
- 第一版只支持精确 `userId`，不引入部门、角色或用户目录同步。

## 访问范围模型

Agent 和每个已绑定 Tool 都使用统一的 `accessScope`：

| accessScope | 管理面文案 | 决策语义 |
| --- | --- | --- |
| `PUBLIC` | 所有用户 | 所有用户可访问，已保存白名单不参与决策 |
| `RESTRICTED` | 仅指定用户 | 默认拒绝，仅白名单中的用户可访问 |

默认值为 `PUBLIC`。未配置 Agent 策略或 Tool 策略时均按 `PUBLIC` 处理。

白名单可以在 `PUBLIC` 状态下保留，但不生效；切回 `RESTRICTED` 后重新生效。V1 不支持 DENY 黑名单。

## 决策规则

### Agent 访问

1. 查询 Agent 的 `accessScope`，没有记录时按 `PUBLIC`。
2. `PUBLIC` 返回允许，原因 `AGENT_PUBLIC_ACCESS`。
3. `RESTRICTED` 且 userId 在 Agent 白名单中，返回允许，原因 `AGENT_USER_WHITELISTED`。
4. `RESTRICTED` 且 userId 不在白名单中，返回拒绝，原因 `AGENT_USER_NOT_WHITELISTED`。
5. 该结果只供业务侧使用，不进入工具层决策。

### Tool 运行时决策

1. 从 `tokenId` 解析 `agentId`、`userId`、`conversationId`。
2. 查询 `agentId + toolId` 工具策略；未绑定返回 `DENY + TOOL_NOT_BOUND`。
3. 查询该 Tool 的 `accessScope`，没有记录时按 `PUBLIC`。
4. `PUBLIC` 跳过人员限制。
5. `RESTRICTED` 且 userId 不在 Tool 白名单中，返回 `DENY + USER_TOOL_ACCESS_DENIED`。
6. 人员检查通过后继续判断原有 `authMode`：
   - `NO_AUTH_REQUIRED`：`ALLOW + NO_AUTH_REQUIRED`。
   - `USER_AUTH_REQUIRED` 或历史空标签：查询 Redis 当前对话授权。
   - Redis 命中：`ALLOW + CONVERSATION_AUTHORIZED`。
   - Redis 未命中：`AUTHORIZATION_REQUIRED + USER_AUTHORIZATION_REQUIRED`。
   - `PER_CALL_AUTH_REQUIRED`：查询并消费 Redis 一次性授权。
   - 一次性授权命中：`ALLOW + PER_CALL_AUTHORIZED`，并删除授权记录。
   - 一次性授权未命中：`AUTHORIZATION_REQUIRED + PER_CALL_AUTHORIZATION_REQUIRED`。

Tool 运行时决策不查询 Agent 的 `accessScope`。

## 数据模型

### agent_user_policy

```text
agent_id
access_scope: PUBLIC | RESTRICTED
created_at
updated_at
UNIQUE(agent_id)
```

### agent_tool_user_policy

```text
agent_id
tool_id
access_scope: PUBLIC | RESTRICTED
created_at
updated_at
UNIQUE(agent_id, tool_id)
```

### agent_user_access_policy

Agent 白名单：

```text
agent_id
user_id
created_at
updated_at
UNIQUE(agent_id, user_id)
```

### agent_tool_user_access_policy

Tool 白名单：

```text
agent_id
tool_id
user_id
created_at
updated_at
UNIQUE(agent_id, tool_id, user_id)
```

策略中心不复制用户目录、Agent 目录或工具详情。

### 数据库脚本

- 新环境初始化：执行 [`sql/policy-center-schema.sql`](../../sql/policy-center-schema.sql)，一次创建基础工具策略表和人员策略表。
- 已执行过基础策略中心 SQL 的存量环境：仅执行 [`sql/user-policy-schema.sql`](../../sql/user-policy-schema.sql)，增量创建本方案新增的 4 张人员策略表。
- 增量脚本使用 `CREATE TABLE IF NOT EXISTS`，可重复执行，不修改 `agent_tool_policy` 及其存量数据。

## 管理端接口

### 查询人员策略

```http
GET /admin/agents/{agentId}/user-policies
```

```json
{
  "agentId": "agent-a",
  "accessScope": "RESTRICTED",
  "agentUsers": [
    {
      "userId": "user-42",
      "updatedAt": "2026-06-11T10:00:00Z"
    }
  ],
  "tools": [
    {
      "toolId": "tool-a",
      "accessScope": "PUBLIC",
      "users": []
    },
    {
      "toolId": "tool-b",
      "accessScope": "RESTRICTED",
      "users": [
        {
          "userId": "user-42",
          "updatedAt": "2026-06-11T10:00:00Z"
        }
      ]
    }
  ],
  "updatedAt": "2026-06-11T10:00:00Z"
}
```

GET 返回当前 Agent 的全部已绑定 Tool。未配置人员策略的 Tool 返回 `accessScope = PUBLIC` 和空白名单。

### 整份保存人员策略

```http
PUT /admin/agents/{agentId}/user-policies
```

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
      "accessScope": "PUBLIC",
      "users": []
    },
    {
      "toolId": "tool-b",
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

规则：

- `accessScope` 缺失或为 `null` 时按 `PUBLIC`。
- `agentUsers` 和 `tools` 必须是数组。
- `tools.toolId` 必须属于当前 Agent 已绑定工具，否则返回 `409 TOOL_NOT_BOUND`。
- 每个 `userId` 既支持单个工号，也支持使用英文/中文逗号、英文/中文分号或换行分隔的批量工号。
- 批量工号自动 trim、过滤空项并去重；没有有效工号时返回 `400 INVALID_REQUEST`。
- 重复 `toolId` 返回 `400 INVALID_REQUEST`。
- 请求为整份覆盖；未出现的 Tool 恢复为 `PUBLIC` 并清除其白名单。
- 显式提交为 `PUBLIC` 的 Agent 或 Tool 可以同时保存白名单。
- 保存操作在单个 MySQL 事务内完成。

## 业务与内部接口

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| `POST` | `/internal/agent-access-decisions` | 按 Agent `accessScope + 白名单` 判断 |
| `GET` | `/internal/users/{userId}/agents` | 返回 PUBLIC Agent 和用户在白名单中的 RESTRICTED Agent |
| `GET` | `/internal/agents/{agentId}/users/{userId}/tools` | 返回 PUBLIC Tool 和用户在白名单中的 RESTRICTED Tool |
| `POST` | `/internal/authorization-decisions` | 在工具绑定后按 Tool `accessScope + 白名单` 判断 |

用户可访问工具列表接口不检查 Agent 权限，也不检查 Redis 当前对话授权。

## 管理面流程

人的策略入口与工具策略入口并列：

1. 前端加载 Agent 网关中的可管理 Agent。
2. 进入人的策略页面后，同时加载工具策略与人员策略。
3. Agent 和每个 Tool 使用分段选择控件展示“所有用户 / 仅指定用户”。
4. 选择“仅指定用户”时启用白名单编辑区，输入框支持一次粘贴多个工号。
5. 选择“所有用户”时白名单可以保留，但页面应提示当前不参与决策。
6. 只允许为已绑定 Tool 配置访问范围和白名单。
7. 保存成功后重新 GET 刷新页面。

## 验收场景

1. 未配置 Agent 或 Tool 时默认为 PUBLIC。
2. PUBLIC Agent 和 Tool 忽略已保存白名单。
3. RESTRICTED Agent 只允许 Agent 白名单用户。
4. RESTRICTED Tool 只允许 Tool 白名单用户。
5. Agent 被限制不影响独立的 Tool 运行时决策。
6. PUBLIC Tool 继续原有 authMode 和 Redis 授权流程。
7. RESTRICTED Tool 非白名单用户不会进入 Redis 授权流程。
8. GET 人员策略返回全部已绑定 Tool，并为未配置 Tool 补齐 PUBLIC。
9. PUT 未包含的 Tool 恢复 PUBLIC 并清除白名单。
10. 未绑定 Tool 的人员配置返回 `409 TOOL_NOT_BOUND`。
11. MySQL 异常时 fail-closed。
12. 批量输入 `z123,c456;d789` 保存为三个独立白名单用户，重复工号只保存一次。

## 当前不做

- 不支持 DENY 黑名单。
- 不支持部门、角色组、用户标签或组织树。
- 不在工具决策中校验 Agent 访问权限。
- 不改变 tokenId、authMode 或 Redis 授权结构。
