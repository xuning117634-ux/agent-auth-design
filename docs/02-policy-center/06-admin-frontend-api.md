# 策略中心管理面前端接口

> 状态：V1 前端联调草案
> 负责人：项目维护者
> 适用版本：V1
> 最后更新：2026-06-09
> 阅读顺序：02-06
> 文档职责：给管理面前端同事说明页面流程、需要调用的后端接口、数据合并规则和错误处理。后端实现契约仍以 [API 契约](02-api-contract.md) 为准。

## 接入边界

管理面前端采用分别直调三方的方式：

| 后端 | 前端用途 |
| --- | --- |
| Agent 网关 | 查询当前管理员可管理的 Agent 列表 |
| MCP 网关 | 查询当前 MCP 全量工具列表 |
| 权限策略中心 | 查询和保存某个 Agent 的工具授权策略 |

当前版本不引入 `departmentId`、按部门过滤工具或部门授权模型。管理面展示的是 MCP 网关返回的当前全量工具列表，用户从全量列表中选择部分工具绑定到 Agent。

## 页面流程

1. 页面初始化时调用 Agent 网关，加载当前管理员有权限管理的 Agent。
2. 管理员选择一个 Agent。
3. 前端并行调用 MCP 网关和权限策略中心：
   - MCP 网关返回当前 MCP 全量工具列表。
   - 权限策略中心返回该 Agent 已保存的工具策略。
4. 前端按 `toolId` 合并工具列表和策略列表，渲染工具矩阵。
5. 用户从全量工具列表中选择部分工具绑定到当前 Agent。
6. 只为已选择绑定的工具配置授权标签；新绑定工具若未选择标签，前端默认 `USER_AUTH_REQUIRED`。
7. 保存时，前端向权限策略中心提交该 Agent 的完整已绑定工具列表。
8. 策略中心只保存提交的 `agentId + toolId + authMode` 绑定关系，未提交工具视为未绑定。
9. 保存成功后重新拉取该 Agent 的策略，刷新页面状态。

## 1. 查询可管理 Agent 列表

Owner：Agent 网关已存在
Caller：管理面前端




## 2. 查询 MCP 工具列表

Owner：MCP 网关提供
Caller：管理面前端




规则：

- 该接口返回当前 MCP 全量工具列表。
- `toolId` 全局唯一。
- 前端只允许绑定 `status = ACTIVE` 的工具。
- 前端从该全量列表中选择部分工具绑定到当前 Agent。
- 工具分类、图标、版本等展示字段后续可扩展，不影响策略保存协议。
- 当工具列表加载失败时，保存按钮必须禁用。


## 3. 查询 Agent 当前工具策略

Owner：权限策略中心
Caller：管理面前端

```http
GET /admin/agents/{agentId}/tool-policies
```

Response:

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

- 只返回已绑定工具。
- 未出现在 `tools` 中的工具视为未绑定。
- 未绑定工具即使存在于 MCP 全量工具列表中，运行时也必须视为 `TOOL_NOT_BOUND`。
- 若历史数据 `authMode` 为空，后端返回时应归一化为 `USER_AUTH_REQUIRED`。
- 查询失败时前端不得使用本地旧数据继续保存。
- 该 GET 接口是管理面前端需要新增的策略中心管理接口。

## 4. 整份保存 Agent 工具策略

Owner：权限策略中心
Caller：管理面前端

```http
PUT /admin/agents/{agentId}/tool-policies
```

Request:

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

Response:

```json
{
  "agentId": "agent-a",
  "toolCount": 2,
  "updatedAt": "2026-06-09T10:00:00Z"
}
```

规则：

- 请求体表示该 Agent 保存后的完整绑定结果。
- `tools: []` 表示解绑该 Agent 的全部工具。
- `tools` 只包含用户从 MCP 全量工具列表中选择绑定的工具。
- 请求中未包含的旧绑定会被删除。
- 策略中心不保存未选择工具；未选择工具运行时返回 `DENY + TOOL_NOT_BOUND`。
- 同一请求中 `toolId` 不得重复。
- `authMode` 缺失或为空时按 `USER_AUTH_REQUIRED` 保存。
- 保存成功后前端重新调用查询接口刷新页面。

## 前端数据规则

授权标签：

```text
NO_AUTH_REQUIRED   = 无需授权
USER_AUTH_REQUIRED = 需要授权
```

矩阵行状态：

```text
未绑定：来自 MCP 全量工具列表，但不提交到 PUT tools
已绑定 + 无需授权：提交 NO_AUTH_REQUIRED
已绑定 + 需要授权：提交 USER_AUTH_REQUIRED
```

默认行为：

- 新勾选绑定的工具默认 `USER_AUTH_REQUIRED`。
- 取消绑定后，该工具不出现在保存请求中。
- 未被选择的工具不会写入策略中心。
- 保存按钮只在 Agent、工具列表和当前策略全部加载成功后启用。
- 有未保存修改时切换 Agent，需要前端提示用户确认。

## 错误处理

通用错误响应：

```json
{
  "code": "INVALID_REQUEST",
  "message": "toolId must not be blank",
  "traceId": "01J..."
}
```

前端处理：

| HTTP / code | 前端行为 |
| --- | --- |
| `400 INVALID_REQUEST` | 展示参数错误，保留当前编辑内容 |
| `401 UNAUTHORIZED` | 跳转登录或触发统一重新认证 |
| `403 FORBIDDEN` | 提示无权限管理该 Agent |
| `404 AGENT_NOT_FOUND` | 提示 Agent 不存在或已不可用，并刷新 Agent 列表 |
| `409 CONFLICT` | 提示策略已变化，重新拉取当前策略 |
| `500 / 503` | 提示系统暂不可用，禁止继续保存 |

## 前端配置

前端需要分别配置三个后端地址：

```text
AGENT_GATEWAY_BASE_URL
MCP_GATEWAY_BASE_URL
POLICY_CENTER_BASE_URL
```

请求头建议：


## 前端验收清单

- 可以加载可管理的 Agent。
- 选择 Agent 后可以加载 MCP 当前全量工具列表和当前工具策略。
- 可以从 MCP 当前全量工具列表中选择部分工具绑定到 Agent。
- 未绑定、无需授权、需要授权三种行状态展示清楚。
- 新绑定工具默认是 `USER_AUTH_REQUIRED`。
- 保存请求只提交已绑定工具。
- `tools: []` 可以解绑全部工具。
- 未选择工具不会写入策略中心，运行时按 `TOOL_NOT_BOUND` 处理。
- 保存成功后重新拉取策略并刷新页面。
- 加载失败、保存失败、无权限和登录失效均有明确提示。
- 前端页面不调用运行时授权、用户授权确认、轮询或清理接口。
