# 人员权限策略接口

> 状态：V1 当前实现与交付要求  
> 适用对象：管理面前端、业务后端、业务 Agent、MCP 网关  
> 适用版本：V1  
> 最后更新：2026-06-15  
> 阅读顺序：02-09  
> 文档职责：集中说明人员权限策略涉及的管理面接口、业务查询接口和工具运行时决策边界。完整系统契约仍以 [API 契约](02-api-contract.md) 为准，方案背景与数据模型参见 [人的权限策略配置方案](07-user-policy-design.md)。

## 接入边界

人员权限策略分为两个彼此独立的层级：

| 策略层级 | 用途 | 是否参与工具运行时决策 |
| --- | --- | --- |
| Agent 人员策略 | 判断用户能否访问或触发 Agent | 否 |
| Tool 人员策略 | 判断用户能否访问 Agent 下的指定工具 | 是 |

统一访问范围：

| accessScope | 管理面文案 | 决策语义 |
| --- | --- | --- |
| `PUBLIC` | 所有用户 | 所有用户可访问，已保存白名单不参与决策 |
| `RESTRICTED` | 仅指定用户 | 默认拒绝，只有白名单用户可以访问 |

未配置 Agent 或 Tool 人员策略时，均按 `PUBLIC` 处理。V1 只支持精确 `userId` 白名单，不支持黑名单、部门、角色组或用户目录同步。

## 接口总览

### 人员策略直接接口

| 编号 | 方法 | 路径 | 用途 |
| --- | --- | --- | --- |
| 1 | `GET` | `/admin/agents/{agentId}/user-policies` | 查询 Agent 与已绑定 Tool 的人员策略 |
| 2 | `PUT` | `/admin/agents/{agentId}/user-policies` | 整份覆盖保存 Agent 与 Tool 人员策略 |

以上两个接口只管理人与 Agent、人与 Tool 的访问范围和白名单，不管理 Tool 的绑定关系与 `authMode`。

### 管理页面完整依赖接口

人员策略页面需要同时依赖工具策略和人员策略，因此策略中心实际需要向管理面提供以下 4 个接口：

| 策略类型 | 方法 | 路径 | 管理内容 |
| --- | --- | --- | --- |
| Tool 工具策略 | `GET` | `/admin/agents/{agentId}/tool-policies` | 查询当前 Agent 已绑定 Tool 及其 `authMode` |
| Tool 工具策略 | `PUT` | `/admin/agents/{agentId}/tool-policies` | 整份保存 Tool 绑定关系及 `authMode` |
| Agent、Tool 人员策略 | `GET` | `/admin/agents/{agentId}/user-policies` | 查询 Agent 访问范围、Agent 白名单和已绑定 Tool 的人员策略 |
| Agent、Tool 人员策略 | `PUT` | `/admin/agents/{agentId}/user-policies` | 整份保存 Agent 与 Tool 的访问范围和白名单 |

三类配置的边界：

| 配置 | 所属接口 | 核心字段 |
| --- | --- | --- |
| Tool 是否绑定 Agent | `/tool-policies` | `tools[].toolId` 是否出现在整份请求中 |
| Tool 是否需要对话授权 | `/tool-policies` | `tools[].authMode` |
| 谁可以访问 Agent | `/user-policies` | `accessScope + agentUsers` |
| 谁可以访问指定 Tool | `/user-policies` | `tools[].accessScope + tools[].users` |

`/user-policies` 中的 `tools` 不是工具策略本身，只是使用 `toolId` 关联已绑定 Tool，并配置该 Tool 的人员访问范围。

### 业务与对外接口

| 编号 | 调用方 | 方法 | 路径 | 用途 |
| --- | --- | --- | --- | --- |
| 3 | 业务后端 / 业务 Agent | `POST` | `/internal/agent-access-decisions` | 判断用户是否可以访问 Agent |
| 4 | 业务后端 | `GET` | `/internal/users/{userId}/agents` | 查询用户可以访问的 Agent |
| 5 | 业务后端 / 业务 Agent | `GET` | `/internal/agents/{agentId}/users/{userId}/tools` | 查询用户在 Agent 下可以访问的工具 |
| 6 | MCP 网关 | `POST` | `/internal/authorization-decisions` | 工具调用时叠加 Tool 人员策略决策 |

V1 暂未实现接口认证。生产接入前必须通过内部网络、上游网关或后续认证机制限制调用方，不能将 `/internal/**` 直接暴露到公网。

## 管理面接入流程

1. 前端从 Agent 网关加载当前管理员可管理的 Agent。
2. 管理员选择 Agent 后，前端并行查询工具策略和人员策略。
3. 前端以工具策略中的已绑定 Tool 为基准，与人员策略中的 Tool 配置按 `toolId` 合并。
4. Agent 和每个 Tool 分别展示“所有用户 / 仅指定用户”。
5. 选择“仅指定用户”后启用白名单编辑；输入框支持粘贴多个工号。
6. Tool 人员策略只能配置在当前 Agent 已绑定的工具上。
7. 如果工具绑定或 `authMode` 发生变化，先保存工具策略。
8. 工具策略保存成功后，再保存人员策略；人员策略请求不得包含已解绑 Tool。
9. 两次保存全部成功后，重新查询工具策略和人员策略并刷新页面。

Agent 人员策略与 Tool 人员策略可以在同一个页面维护，但两者的决策用途必须在页面文案中明确区分。

## 当前代码兼容性

当前后端代码与上述接口拆分方式兼容：

- `AdminToolPolicyController` 独立处理 `/tool-policies`，保存 Tool 绑定关系和 `authMode`。
- `AdminUserPolicyController` 独立处理 `/user-policies`，保存 Agent 和 Tool 的人员访问范围及白名单。
- 保存 Tool 人员策略时，后端会检查 `toolId` 是否属于当前 Agent 的已绑定 Tool。
- `/user-policies` 的一次 PUT 会在单个 MySQL 事务内覆盖 Agent 人员策略和全部 Tool 人员策略。
- 工具运行时决策先检查 Tool 是否绑定，再检查 Tool 人员策略，因此未绑定 Tool 不会因残留人员策略获得访问权限。

当前实现没有把 Tool 的 `authMode` 放入 `/user-policies`，旧的 `/tool-policies` 接口契约不需要调整。

## 当前一致性风险

### Tool 解绑不会自动清理人员策略

当前 `/tool-policies` 解绑 Tool 时，只删除 `agent_tool_policy` 中的绑定关系，不会同步删除：

```text
agent_tool_user_policy
agent_tool_user_access_policy
```

数据库表之间也没有外键级联删除。由此产生以下行为：

1. Tool 解绑后，人员策略 GET 不再返回该 Tool，运行时也会先返回 `TOOL_NOT_BOUND`，所以残留数据暂时不会生效。
2. 如果以后重新绑定相同 `agentId + toolId`，旧的 `accessScope` 和白名单可能重新出现并恢复生效。
3. 如果重新绑定后前端立即整份保存人员策略，旧数据会被覆盖；但不能依赖前端操作保证数据正确性。

生产接入前应由后端补齐解绑清理：

- `/tool-policies` 整份保存时，计算本次被解绑的 `toolId`。
- 同步删除被解绑 Tool 的 `agent_tool_user_policy` 和 `agent_tool_user_access_policy`。
- 工具策略保存和人员策略清理应放在同一个数据库事务中。
- Tool 重新绑定后，如果没有重新配置人员策略，应按 `PUBLIC + 空白名单` 处理。

不建议仅依靠定时任务清理，因为解绑与重新绑定之间可能存在旧策略重新生效窗口。

### 两个 PUT 不是同一个事务

`PUT /tool-policies` 和 `PUT /user-policies` 是两个独立 HTTP 请求，不属于同一个数据库事务。

可能出现：

1. 工具策略保存成功。
2. 人员策略保存失败。
3. 页面和数据库处于“工具已更新、人员策略未更新”的部分成功状态。

前端处理要求：

- 固定先保存 `/tool-policies`，再保存 `/user-policies`。
- 新绑定 Tool 必须先完成工具策略保存，否则人员策略保存会返回 `409 TOOL_NOT_BOUND`。
- 人员策略保存失败时，不自动回滚工具策略，也不能提示整体保存成功。
- 保留用户当前编辑内容，并重新查询两个 GET 接口。
- 将服务器最新状态与未保存内容重新合并，提示用户确认后再次保存。
- 两个 PUT 全部成功后，才显示“配置保存成功”。

后续如果管理面要求严格的全量原子保存，可以新增后端聚合保存接口；V1 保持现有 4 个接口，避免破坏已有契约。

### 整份覆盖的并发风险

两个 PUT 都使用整份覆盖语义，目前没有版本号、ETag 或 `If-Match` 控制。多个管理员同时编辑同一个 Agent 时，后保存的请求可能覆盖先保存的结果。

V1 前端至少应：

- 保存前保留最近一次 GET 返回的 `updatedAt`。
- 保存后强制重新 GET，不直接以本地请求体作为最终状态。
- 页面长时间停留或重新获得焦点时提示刷新策略。

生产化阶段建议增加版本字段或乐观锁冲突检测。

## 1. 查询 Agent 当前人员策略

Owner：权限策略中心  
Caller：管理面前端

```http
GET /admin/agents/{agentId}/user-policies
```

请求示例：

```http
GET http://localhost:18080/admin/agents/agent-a/user-policies
X-Trace-Id: trace-20260615-001
```

成功响应：

```json
{
  "agentId": "agent-a",
  "accessScope": "RESTRICTED",
  "agentUsers": [
    {
      "userId": "user-42",
      "updatedAt": "2026-06-15T10:00:00Z"
    }
  ],
  "tools": [
    {
      "toolId": "crm.customer.query",
      "accessScope": "PUBLIC",
      "users": []
    },
    {
      "toolId": "crm.customer.delete",
      "accessScope": "RESTRICTED",
      "users": [
        {
          "userId": "user-42",
          "updatedAt": "2026-06-15T10:00:00Z"
        }
      ]
    }
  ],
  "updatedAt": "2026-06-15T10:00:00Z"
}
```

规则：

- `accessScope` 是 Agent 访问范围。
- `agentUsers` 是 Agent 白名单。
- `tools` 返回当前 Agent 的全部已绑定工具，不只返回已配置人员策略的工具。
- 已绑定但未配置人员策略的 Tool 返回 `accessScope = PUBLIC` 和空白名单。
- Agent 未配置人员策略时返回 `accessScope = PUBLIC` 和空白名单。
- 历史残留的未绑定 Tool 人员策略不返回给管理面。
- `PUBLIC` 状态下可以返回已保存白名单，但该名单不参与访问判断。
- 查询失败时，前端不得使用旧数据继续保存。

## 2. 整份保存 Agent 人员策略

Owner：权限策略中心  
Caller：管理面前端

```http
PUT /admin/agents/{agentId}/user-policies
```

请求示例：

```http
PUT http://localhost:18080/admin/agents/agent-a/user-policies
Content-Type: application/json
X-Trace-Id: trace-20260615-002

{
  "accessScope": "RESTRICTED",
  "agentUsers": [
    {
      "userId": "z123,c456;d789"
    }
  ],
  "tools": [
    {
      "toolId": "crm.customer.query",
      "accessScope": "PUBLIC",
      "users": []
    },
    {
      "toolId": "crm.customer.delete",
      "accessScope": "RESTRICTED",
      "users": [
        {
          "userId": "z123,c456"
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
  "agentUserRuleCount": 3,
  "toolUserRuleCount": 2,
  "updatedAt": "2026-06-15T10:05:00Z"
}
```

规则：

- 请求体表示该 Agent 保存后的完整人员策略，不是增量更新。
- `accessScope` 缺失或为 `null` 时按 `PUBLIC` 保存。
- `agentUsers` 和 `tools` 必须传数组，不能为 `null`。
- `agentUsers: []` 表示清空 Agent 白名单。
- `tools: []` 表示全部 Tool 恢复为 `PUBLIC` 并清空 Tool 白名单。
- 请求中未出现的已绑定 Tool 同样恢复为 `PUBLIC` 并清空白名单。
- 显式提交为 `PUBLIC` 的 Agent 或 Tool 可以保留白名单，但决策时忽略。
- `tools.toolId` 必须属于该 Agent 当前已绑定工具，否则返回 `409 + TOOL_NOT_BOUND`。
- 同一请求中的 `toolId` 不能重复。
- 保存操作在单个 MySQL 事务内完成。

批量工号规则：

- `userId` 支持单个工号。
- 一个 `userId` 字段也可以包含多个工号。
- 支持英文逗号、中文逗号、英文分号、中文分号和换行分隔。
- 后端会 trim、过滤空项，并按首次出现顺序去重。
- 拆分后没有有效工号时返回 `400 + INVALID_REQUEST`。

前端也可以先拆分为多个对象提交：

```json
{
  "agentUsers": [
    {
      "userId": "z123"
    },
    {
      "userId": "c456"
    }
  ],
  "tools": []
}
```

## 3. 判断用户是否可以访问 Agent

Owner：权限策略中心  
Caller：业务后端、业务 Agent

业务侧应在触发 Agent 前调用该接口。该结果不参与 MCP 工具调用授权决策。

```http
POST /internal/agent-access-decisions
```

请求示例：

```http
POST http://localhost:18080/internal/agent-access-decisions
Content-Type: application/json
X-Trace-Id: trace-20260615-003

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

决策原因：

| reason | allowed | 含义 |
| --- | --- | --- |
| `AGENT_PUBLIC_ACCESS` | `true` | Agent 为 `PUBLIC` |
| `AGENT_USER_WHITELISTED` | `true` | Agent 为 `RESTRICTED`，用户在白名单中 |
| `AGENT_USER_NOT_WHITELISTED` | `false` | Agent 为 `RESTRICTED`，用户不在白名单中 |

业务调用要求：

- `allowed = false` 时，业务后端或业务 Agent 应终止触发 Agent。
- HTTP 超时、`5xx` 或无法解析响应时，应按失败关闭处理。
- 不得使用本接口结果替代 Tool 人员策略和工具授权决策。

## 4. 查询用户可以访问的 Agent

Owner：权限策略中心  
Caller：业务后端

```http
GET /internal/users/{userId}/agents
```

请求示例：

```http
GET http://localhost:18080/internal/users/user-42/agents
X-Trace-Id: trace-20260615-004
```

成功响应：

```json
{
  "userId": "user-42",
  "agents": [
    {
      "agentId": "agent-a",
      "reason": "AGENT_USER_WHITELISTED"
    },
    {
      "agentId": "agent-b",
      "reason": "AGENT_PUBLIC_ACCESS"
    }
  ]
}
```

规则：

- 返回 `PUBLIC` Agent，以及当前用户在白名单中的 `RESTRICTED` Agent。
- 返回范围限定为策略中心已知 Agent。
- 策略中心已知 Agent 指存在工具策略或人员策略记录的 Agent。
- 策略中心不复制 Agent 名称、描述、图标和状态；业务侧需要按 `agentId` 与 Agent 目录合并展示。
- 没有可访问 Agent 时返回空数组，不返回 `404`。

## 5. 查询用户在 Agent 下可以访问的工具

Owner：权限策略中心  
Caller：业务后端、业务 Agent

```http
GET /internal/agents/{agentId}/users/{userId}/tools
```

请求示例：

```http
GET http://localhost:18080/internal/agents/agent-a/users/user-42/tools
X-Trace-Id: trace-20260615-005
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
    },
    {
      "serverName": "财经服务",
      "toolName": "删除客户",
      "toolId": "crm.customer.delete",
      "authMode": "USER_AUTH_REQUIRED"
    }
  ]
}
```

规则：

- 只返回当前 Agent 已绑定的工具。
- 返回 `PUBLIC` Tool，以及当前用户在白名单中的 `RESTRICTED` Tool。
- 不检查当前用户是否可以访问 Agent。
- 不检查 Redis 当前对话授权。
- `authMode` 只表示工具是否需要对话授权，不表示当前对话已经授权。
- 历史空 `authMode` 按 `USER_AUTH_REQUIRED` 返回。
- `serverName` 来自 `agent_policy_tool.service_name`，为空时回退为 `service_id`。
- `toolName` 来自 `agent_policy_tool.tool_name`。
- `serverName` 和 `toolName` 仅用于展示，不参与授权判断；目录记录缺失时返回 `null`。
- 没有可访问工具时返回空数组，不返回 `404`。

## 6. 工具运行时人员策略决策

Owner：权限策略中心  
Caller：MCP 网关

人员策略不新增运行时接口，而是增强现有工具授权决策：

```http
POST /internal/authorization-decisions
```

请求体保持不变：

```json
{
  "tokenId": "agent-a:user-42:conversation-99",
  "toolId": "crm.customer.delete"
}
```

Tool 为 `RESTRICTED` 且用户不在白名单时：

```json
{
  "decision": "DENY",
  "reason": "USER_TOOL_ACCESS_DENIED"
}
```

人员策略相关决策顺序：

1. 从 `tokenId` 解析 `agentId`、`userId` 和 `conversationId`。
2. 检查 `agentId + toolId` 是否已绑定，未绑定返回 `DENY + TOOL_NOT_BOUND`。
3. Tool 为 `PUBLIC` 时跳过人员限制。
4. Tool 为 `RESTRICTED` 时，只允许白名单用户继续执行。
5. 非白名单用户返回 `DENY + USER_TOOL_ACCESS_DENIED`，不再查询 Redis。
6. 人员检查通过后，继续执行原有 `authMode` 和 Redis 当前对话授权判断。

重要边界：

- 不检查用户是否可以访问 Agent。
- Agent 为 `RESTRICTED` 且用户不在 Agent 白名单中，也不影响独立的 Tool 运行时决策。
- `NO_AUTH_REQUIRED` Tool 在人员检查通过后直接返回 `ALLOW + NO_AUTH_REQUIRED`。
- `USER_AUTH_REQUIRED` Tool 必须同时满足 Tool 人员策略和 Redis 当前对话授权。
- MCP 网关只有在 `decision = ALLOW` 时才能继续获取 Cookie 并调用 MCP Server。

## 通用错误处理

错误响应：

```json
{
  "code": "INVALID_REQUEST",
  "message": "request is invalid",
  "traceId": "trace-20260615-006"
}
```

| HTTP / code | 场景 | 调用方处理 |
| --- | --- | --- |
| `400 INVALID_REQUEST` | 必填字段、数组、枚举或批量工号非法 | 修正请求；管理面保留当前编辑内容 |
| `409 TOOL_NOT_BOUND` | 保存了当前 Agent 未绑定 Tool 的人员策略 | 刷新工具策略与人员策略后重新编辑 |
| `503 POLICY_STORE_UNAVAILABLE` | MySQL 人员策略查询或保存失败 | 按失败关闭处理，不得继续访问或调用工具 |
| `500 INTERNAL_ERROR` | 未分类内部错误 | 按失败关闭处理并使用 `traceId` 排查 |

人员策略查询和决策依赖 MySQL。数据库异常时不回退为 `PUBLIC`，必须 fail-closed。

## 前端数据规则

访问范围：

```text
PUBLIC     = 所有用户
RESTRICTED = 仅指定用户
```

页面规则：

- Agent 和每个已绑定 Tool 独立维护 `accessScope`。
- Agent 访问范围不作为 Tool 访问范围的父级开关。
- 选择 `RESTRICTED` 时启用白名单编辑区域。
- 选择 `PUBLIC` 时可以保留白名单，但应提示当前名单不生效。
- Tool 列表以工具策略接口返回的已绑定工具为准。
- 有未保存修改时切换 Agent，需要前端提示用户确认。
- 保存按钮只在工具策略和人员策略均加载成功后启用。
- 保存成功后重新调用 GET 接口，不以本地请求体直接覆盖页面状态。

## 开发与部署要求

### 后端开发

交付前需要确认：

- 保留现有 `/tool-policies` 与 `/user-policies` 接口，不将 `authMode` 合并到人员策略接口。
- Tool 人员策略保存继续校验 Tool 已绑定，未绑定时返回 `409 TOOL_NOT_BOUND`。
- 补充 Tool 解绑时同步清理 Tool 人员策略的实现。
- 工具解绑和人员策略清理使用同一个事务，任何一步失败均整体回滚。
- 增加“解绑后重新绑定默认 PUBLIC”的单元测试、Repository 测试和 HTTP 验证场景。
- 保持运行时决策顺序为“工具绑定校验、Tool 人员策略、authMode、Redis 授权”。

### 前端开发

交付前需要确认：

- 页面初始化同时调用工具策略 GET 和人员策略 GET。
- 工具绑定、`authMode`、Agent 人员策略和 Tool 人员策略在状态模型中分别维护。
- Tool 人员策略只展示当前已绑定 Tool。
- 新绑定 Tool 默认 `USER_AUTH_REQUIRED`，人员访问范围默认 `PUBLIC`。
- 保存时先调用工具策略 PUT，再调用人员策略 PUT。
- 人员策略 PUT 不包含本次已解绑 Tool。
- 任一请求失败时不显示整体保存成功，并提供重试或重新加载入口。
- 切换 Agent、关闭页面或刷新页面前，对未保存内容进行提示。

### 部署顺序

建议按以下顺序发布：

1. 执行人员策略增量 SQL。已存在基础工具策略表的环境只执行 `sql/user-policy-schema.sql`。
2. 发布包含人员策略接口和 Tool 解绑清理逻辑的策略中心后端。
3. 使用真实 MySQL 执行 `scripts/verify-user-policy.ps1`，确认管理接口、业务接口和运行时决策。
4. 发布管理面前端，并配置 `POLICY_CENTER_BASE_URL`。
5. 在测试环境完成新增、修改、解绑、重新绑定和部分保存失败场景验证。
6. 验证通过后再发布生产环境。

部署检查：

- 4 张人员策略表创建成功，唯一索引完整。
- 策略中心可以同时访问工具策略表和人员策略表。
- 管理面能够访问 4 个策略中心管理接口。
- `/internal/**` 仅允许受信网络或上游网关调用。
- MySQL 异常时接口 fail-closed，不回退为 `PUBLIC`。
- 日志可以通过 `X-Trace-Id` 关联一次保存和决策请求。

## 联调验收清单

- 未配置人员策略时，Agent 和 Tool 均返回 `PUBLIC`。
- `PUBLIC` Agent 对任意用户返回 `AGENT_PUBLIC_ACCESS`。
- `RESTRICTED` Agent 只允许白名单用户。
- 用户可访问 Agent 列表同时包含 PUBLIC Agent 和命中的 RESTRICTED Agent。
- `PUBLIC` Tool 对任意用户出现在可访问工具列表中。
- `RESTRICTED` Tool 只对白名单用户返回。
- 可访问工具列表不检查 Agent 权限和 Redis 授权。
- Tool 运行时决策不检查 Agent 权限。
- RESTRICTED Tool 的非白名单用户返回 `USER_TOOL_ACCESS_DENIED`。
- 新绑定 Tool 未配置人员策略时默认为 `PUBLIC`。
- 未绑定 Tool 的人员策略保存返回 `409 TOOL_NOT_BOUND`。
- Tool 解绑后，其访问范围和白名单被同步清理。
- Tool 解绑后重新绑定，人员策略默认为 `PUBLIC + 空白名单`。
- 工具策略 PUT 成功、人员策略 PUT 失败时，页面不提示整体保存成功。
- 批量工号支持逗号、分号和换行，并自动去重。
- `PUBLIC` 状态保存的白名单切换为 `RESTRICTED` 后可以重新生效。
- MySQL 不可用时，查询和决策均按失败关闭处理。

本地启动策略中心后，可以执行人员策略验收脚本：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\verify-user-policy.ps1
```
