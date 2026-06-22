# Skill 人员权限策略接口

> 状态：V1 当前实现
>
> 适用对象：管理面前端、业务后端、业务 Agent
>
> 最后更新：2026-06-22

## 接入边界

Skill 人员权限复用现有 `PUBLIC / RESTRICTED` 和精确 `userId` 白名单模型，但使用独立的数据表和接口。Skill 目录由外部系统维护，策略中心只查询 `agent_policy_skill` 中 `status = 1` 的记录。

- `PUBLIC`：所有用户可见，已保存白名单不参与判断。
- `RESTRICTED`：仅白名单用户可见。
- 未配置策略时默认 `PUBLIC`。
- Skill 权限不检查 Agent 人员权限，不接入 Tool 的 `/internal/authorization-decisions`，也不查询 Redis 对话授权。

## 接口总览

| 类型 | 方法 | 路径 | 用途 |
| --- | --- | --- | --- |
| 管理面 | `GET` | `/admin/agents/{agentId}/skill-user-policies` | 查询全部已绑定 Skill 及人员策略 |
| 管理面 | `PUT` | `/admin/agents/{agentId}/skill-user-policies` | 整份覆盖保存 Skill 人员策略 |
| 业务查询 | `GET` | `/internal/agents/{agentId}/users/{userId}/skills` | 查询用户在 Agent 下可访问的 Skill |

## 查询管理策略

```http
GET /admin/agents/{agentId}/skill-user-policies
```

```json
{
  "agentId": "agent-a",
  "skills": [
    {
      "skillId": "skill-a",
      "skillName": "财经分析",
      "label": "finance",
      "description": "财经数据分析",
      "accessScope": "RESTRICTED",
      "users": [
        {
          "userId": "z123",
          "updatedAt": "2026-06-22T10:00:00Z"
        }
      ]
    }
  ],
  "updatedAt": "2026-06-22T10:00:00Z"
}
```

GET 只返回当前已绑定的 Skill。已绑定但未配置的 Skill 返回 `PUBLIC` 和空白名单；`status = 0` 的 Skill 不返回。

## 整份覆盖保存

```http
PUT /admin/agents/{agentId}/skill-user-policies
Content-Type: application/json
```

```json
{
  "skills": [
    {
      "skillId": "skill-a",
      "accessScope": "RESTRICTED",
      "users": [
        {
          "userId": "z123,c456;d789"
        }
      ]
    }
  ]
}
```

```json
{
  "agentId": "agent-a",
  "skillPolicyCount": 1,
  "skillUserRuleCount": 3,
  "updatedAt": "2026-06-22T10:00:00Z"
}
```

保存规则：

- `skills: []` 清空该 Agent 的全部 Skill 人员策略。
- 未出现在请求中的 Skill 恢复默认 `PUBLIC` 并清空白名单。
- `accessScope` 缺失或为 `null` 时按 `PUBLIC`。
- `PUBLIC` 可以保留白名单，但决策时忽略。
- 批量工号支持中英文逗号、中英文分号和换行，自动去空格、过滤空项并去重。
- 重复 `skillId`、空工号或非法枚举返回 `400 INVALID_REQUEST`。
- 未绑定 Skill 返回 `409 SKILL_NOT_BOUND`。
- 保存操作在单个 MySQL 事务中完成。

## 查询用户可访问 Skill

```http
GET /internal/agents/{agentId}/users/{userId}/skills
```

```json
{
  "agentId": "agent-a",
  "userId": "z123",
  "skills": [
    {
      "skillId": "skill-a",
      "skillName": "财经分析",
      "label": "finance",
      "description": "财经数据分析"
    }
  ]
}
```

该接口返回 PUBLIC Skill 和用户已进入白名单的 RESTRICTED Skill。接口不校验 Agent 访问权限，调用方如需组合 Agent 权限，应在业务层另行调用 Agent 访问决策接口。

## 数据库与部署

- 新环境执行 `sql/policy-center-schema.sql`。
- 存量环境执行 `sql/skill-user-policy-schema.sql`，只新增两张策略中心自有表。
- 本地联调可额外执行 `sql/local-agent-policy-skill-schema.sql` 模拟外部 Skill 目录表，生产环境不得用该脚本覆盖内网目录表。
- MySQL 异常统一返回 `503 POLICY_STORE_UNAVAILABLE`。
- 真实 HTTP 验证使用 `scripts/verify-skill-user-policy.ps1`，数据库密码通过 `POLICY_CENTER_DB_PASSWORD` 环境变量传入。
