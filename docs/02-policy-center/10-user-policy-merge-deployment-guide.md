# 用户策略功能合入与部署说明

> 状态：合入交接  
> 功能负责人：牛伟才  
> 目标读者：策略中心主分支维护者、后端开发、管理面前端、测试与部署人员  
> 基线分支：`origin/main`  
> 功能分支：`origin/codex/nwc`  
> 最后更新：2026-06-15  
> 相关文档：[用户策略设计](07-user-policy-design.md)、[用户策略接口](09-user-policy-api.md)

## 1. 交付范围

本次交付新增“用户维度的 Agent 与 Tool 访问策略”，主要能力如下：

- Agent 支持 `PUBLIC / RESTRICTED` 访问范围。
- Tool 支持独立的 `PUBLIC / RESTRICTED` 人员访问范围。
- `RESTRICTED` 使用精确 `userId` 白名单。
- 管理面可以整份查询和保存 Agent、Tool 人员策略。
- 业务侧可以判断用户能否访问 Agent。
- 业务侧可以查询用户可访问的 Agent 和 Tool。
- 工具运行时决策新增 Tool 人员策略检查。
- Tool 人员策略通过后，继续执行原有 `authMode` 和 Redis 对话授权逻辑。
- Agent 人员策略不参与工具运行时决策。

本次没有修改：

- `tokenId` 格式。
- Redis 授权 Key、TTL 和清理逻辑。
- 现有 Tool 绑定及 `authMode` 接口路径。
- Maven 依赖和应用配置项。
- Agent 网关、MCP 网关生产代码。

## 2. 合入提交

### 2.1 必须合入

| 提交 | 内容 | 说明 |
| --- | --- | --- |
| `50f59a8` | `feat: add user access scope policies` | 用户策略核心 Java 代码、测试、接口文档和完整初始化 SQL |
| `cf2c71c` | `docs: add user policy incremental schema` | 新增存量环境专用增量 SQL |

### 2.2 接口交接文档

| 提交 | 内容 | 说明 |
| --- | --- | --- |
| `7a2b638` | `docs: add user policy API and admin prototype` | 新增 `09-user-policy-api.md`，同时包含管理面假 HTML 原型 |

`7a2b638` 同时包含以下文件：

```text
docs/02-policy-center/07-user-policy-design.md
docs/02-policy-center/09-user-policy-api.md
docs/doc_tmp/agent-gateway-admin-prototype.html
```

其中 `agent-gateway-admin-prototype.html` 只用于 UI 与交互评审，不属于生产前端代码。若主分支不需要保留原型，不建议直接 cherry-pick 整个 `7a2b638`，可以只选择 `07` 和 `09` 两份文档。

### 2.3 不建议整分支直接合入

`codex/nwc` 还包含 TMG 汇报材料和其他临时文档。为降低冲突及无关文件进入主分支的风险，建议按提交 cherry-pick，不建议直接把整个功能分支合并到 `main`。

推荐操作：

```powershell
git checkout main
git pull origin main
git checkout -b merge/user-policy

git cherry-pick 50f59a8
git cherry-pick cf2c71c
```

如需接口交接文档但不需要 HTML 原型：

```powershell
git checkout 7a2b638 -- `
  docs/02-policy-center/07-user-policy-design.md `
  docs/02-policy-center/09-user-policy-api.md

git commit -m "docs: add user policy API guide"
```

## 3. 文件变更统计

核心实现提交 `50f59a8`：

| 类型 | 数量 |
| --- | ---: |
| 新增文件 | 42 |
| 修改文件 | 12 |
| 合计 | 54 |
| 新增代码行 | 3395 |
| 删除代码行 | 22 |

增量 SQL 提交 `cf2c71c`：

| 类型 | 数量 |
| --- | ---: |
| 新增文件 | 1 |
| 修改文件 | 3 |
| 合计 | 4 |

去除两个提交间的重复文档后，用户策略核心交付共涉及 **55 个唯一文件**：

- 新增 43 个。
- 修改 12 个。

`09-user-policy-api.md` 和管理面假 HTML 原型不计入上述核心实现统计。

## 4. 生产代码变化

### 4.1 Controller

新增 2 个文件：

```text
src/main/java/com/huawei/it/roma/liveeda/policycenter/api/controller/
├── AdminUserPolicyController.java
└── UserPolicyQueryController.java
```

职责：

- `AdminUserPolicyController`：提供人员策略管理面 GET/PUT。
- `UserPolicyQueryController`：提供 Agent 访问判断、可访问 Agent、可访问 Tool 查询。

### 4.2 API DTO

新增 12 个文件：

```text
src/main/java/com/huawei/it/roma/liveeda/policycenter/api/dto/
├── AccessibleAgentItemResponse.java
├── AccessibleAgentListResponse.java
├── AccessibleToolItemResponse.java
├── AccessibleToolListResponse.java
├── AgentAccessDecisionRequest.java
├── AgentAccessDecisionResponse.java
├── SaveUserPolicyRequest.java
├── ToolUserPolicyItemRequest.java
├── ToolUserPolicyItemResponse.java
├── UserPolicyItemRequest.java
├── UserPolicyItemResponse.java
└── UserPolicyResponse.java
```

### 4.3 Domain

新增 7 个文件：

```text
src/main/java/com/huawei/it/roma/liveeda/policycenter/domain/
├── AccessScope.java
├── AgentAccessDecision.java
├── AgentAccessReason.java
├── AgentUserPolicy.java
├── ToolUserAccessRule.java
├── ToolUserPolicy.java
└── UserAccessRule.java
```

修改 1 个文件：

```text
src/main/java/com/huawei/it/roma/liveeda/policycenter/domain/DecisionReason.java
```

变化：

```text
新增 USER_TOOL_ACCESS_DENIED
```

### 4.4 Service

新增 8 个文件：

```text
src/main/java/com/huawei/it/roma/liveeda/policycenter/service/
├── ToolUserPolicyEvaluator.java
├── ToolUserPolicyUpdate.java
├── ToolUserPolicyView.java
├── UserAccessUpdate.java
├── UserPolicySaveResult.java
├── UserPolicyService.java
├── UserPolicyUpdate.java
└── UserPolicyView.java
```

修改 1 个存量文件：

```text
src/main/java/com/huawei/it/roma/liveeda/policycenter/service/AuthorizationDecisionService.java
```

关键变化：

1. 注入 `ToolUserPolicyEvaluator`。
2. 工具绑定校验通过后，增加 Tool 人员策略判断。
3. 非白名单用户返回 `DENY + USER_TOOL_ACCESS_DENIED`。
4. 人员策略存储异常返回 `DENY + POLICY_STORE_UNAVAILABLE`。
5. 人员策略通过后才继续原有 `authMode` 与 Redis 判断。
6. 保留旧的两参数、三参数构造方法，默认使用 `allowAll()`，降低存量单元测试和手工实例化代码的兼容风险。

该文件是与同事存量授权决策开发最可能产生冲突的位置，合并时必须人工确认决策顺序。

### 4.5 Repository 与 MyBatis

新增 7 个 Java 文件：

```text
src/main/java/com/huawei/it/roma/liveeda/policycenter/
├── repository/UserPolicyRepository.java
└── infrastructure/mybatis/
    ├── AgentUserPolicyRecord.java
    ├── MyBatisUserPolicyRepository.java
    ├── ToolUserAccessPolicyRecord.java
    ├── ToolUserPolicyRecord.java
    ├── UserAccessPolicyRecord.java
    └── UserPolicyMapper.java
```

新增 Mapper XML：

```text
src/main/resources/mapper/UserPolicyMapper.xml
```

Mapper 包含：

- Agent 和 Tool 访问范围查询。
- Agent 和 Tool 白名单查询。
- 白名单命中判断。
- 策略中心已知 Agent 查询。
- Agent 人员策略 upsert。
- Agent 和 Tool 人员策略整份覆盖删除、重建。

## 5. 接口变化

### 5.1 新增管理面接口

```http
GET /admin/agents/{agentId}/user-policies
PUT /admin/agents/{agentId}/user-policies
```

这两个接口只负责：

- 人与 Agent 的访问范围及白名单。
- 人与 Tool 的访问范围及白名单。

Tool 绑定关系与 `authMode` 仍使用存量接口：

```http
GET /admin/agents/{agentId}/tool-policies
PUT /admin/agents/{agentId}/tool-policies
```

### 5.2 新增业务接口

```http
POST /internal/agent-access-decisions
GET  /internal/users/{userId}/agents
GET  /internal/agents/{agentId}/users/{userId}/tools
```

### 5.3 增强存量接口

```http
POST /internal/authorization-decisions
```

请求体没有变化。新增结果：

```json
{
  "decision": "DENY",
  "reason": "USER_TOOL_ACCESS_DENIED"
}
```

运行时顺序必须保持：

1. 解析 `tokenId`。
2. 检查 Tool 是否绑定 Agent。
3. 检查 Tool 人员策略。
4. 判断 `authMode`。
5. 对 `USER_AUTH_REQUIRED` 查询 Redis 对话授权。

Agent 人员策略不进入该决策链路。

## 6. 数据库变化

### 6.1 新增表

本次新增 4 张 MySQL 表：

| 表名 | 用途 | 唯一键 |
| --- | --- | --- |
| `agent_user_policy` | Agent 的 `PUBLIC / RESTRICTED` 访问范围 | `agent_id` |
| `agent_tool_user_policy` | Agent 下 Tool 的人员访问范围 | `agent_id + tool_id` |
| `agent_user_access_policy` | Agent 白名单 | `agent_id + user_id` |
| `agent_tool_user_access_policy` | Agent 下 Tool 白名单 | `agent_id + tool_id + user_id` |

所有表：

- 使用 InnoDB。
- 使用 `utf8mb4`。
- 包含 `created_at` 和 `updated_at`。
- 未配置策略时由代码按 `PUBLIC` 处理。

### 6.2 SQL 文件

修改：

```text
sql/policy-center-schema.sql
```

用途：新环境一次性初始化工具策略表和用户策略表。

新增：

```text
sql/user-policy-schema.sql
```

用途：已经执行过存量策略中心 SQL 的环境，仅增量创建 4 张用户策略表。

增量脚本使用 `CREATE TABLE IF NOT EXISTS`，不会修改或删除：

```text
agent_tool_policy
```

### 6.3 存量环境执行

生产或测试环境已经存在 `policy_center.agent_tool_policy` 时：

```powershell
mysql -h <host> -P <port> -u <user> -p < sql/user-policy-schema.sql
```

注意：

- 脚本包含 `CREATE DATABASE IF NOT EXISTS policy_center` 和 `USE policy_center`。
- 执行账户需要建库权限；如果环境由 DBA 预建数据库，可由 DBA 审核后去掉建库语句，只在目标库执行建表部分。
- 执行前确认实际数据库名与应用 `POLICY_CENTER_DB_URL` 一致。
- 本次没有数据回填要求，空表即表示默认 `PUBLIC`。

### 6.4 新环境执行

```powershell
mysql -h <host> -P <port> -u <user> -p < sql/policy-center-schema.sql
```

不要在同一个环境重复选择“完整初始化”和“增量脚本”作为两套迁移流程。虽然建表语句可重复执行，部署记录中仍应明确实际执行了哪一个脚本。

### 6.5 数据库兼容与已知限制

当前 4 张人员策略表与 `agent_tool_policy` 没有外键关系。

当前代码存在一个需要在生产合入前确认的限制：

- Tool 从 `/tool-policies` 解绑时，只删除 Tool 绑定。
- 对应的 `agent_tool_user_policy` 和 `agent_tool_user_access_policy` 不会自动删除。
- Tool 解绑期间残留策略不会生效，因为运行时优先返回 `TOOL_NOT_BOUND`。
- 相同 Tool 重新绑定后，旧人员策略可能重新生效。

建议主分支维护者在生产发布前补充：

1. 计算本次解绑的 `toolId`。
2. 同事务删除对应 Tool 访问范围和白名单。
3. 增加“解绑后重新绑定默认 PUBLIC”的测试。

详细要求参见 [用户策略接口](09-user-policy-api.md) 的“当前一致性风险”和“开发与部署要求”。

## 7. 测试与验证文件

新增 3 个测试文件：

```text
src/test/java/com/huawei/it/roma/liveeda/policycenter/
├── api/controller/AdminUserPolicyControllerTest.java
├── api/controller/UserPolicyQueryControllerTest.java
└── service/UserPolicyServiceTest.java
```

修改 1 个存量测试：

```text
src/test/java/com/huawei/it/roma/liveeda/policycenter/service/AuthorizationDecisionServiceTest.java
```

新增 HTTP 验证脚本：

```text
scripts/verify-user-policy.ps1
```

脚本覆盖：

- 默认 `PUBLIC`。
- Agent 白名单允许和拒绝。
- 用户可访问 Agent 列表。
- PUBLIC/RESTRICTED Tool 列表。
- Tool 人员策略运行时允许和拒绝。
- `NO_AUTH_REQUIRED`。
- `USER_AUTH_REQUIRED` 与 Redis 授权。
- 批量工号解析和去重。
- PUT 未包含 Tool 恢复 PUBLIC。
- 未绑定 Tool 返回 `TOOL_NOT_BOUND`。
- 验证数据自动清理。

当前功能分支验证记录：

```text
验证日期：2026-06-15
执行命令：mvn test
结果：Tests run: 63, Failures: 0, Errors: 0, Skipped: 0
状态：BUILD SUCCESS
```

同事合入主分支后，测试数量可能因主分支新增用例而增加，但不应出现失败、错误或跳过数量异常。

## 8. 文档变化

核心提交修改或新增：

```text
docs/02-policy-center/01-policy-center-spec.md
docs/02-policy-center/02-api-contract.md
docs/02-policy-center/03-data-model.md
docs/02-policy-center/04-acceptance-scenarios.md
docs/02-policy-center/05-development-progress.md
docs/02-policy-center/06-admin-frontend-api.md
docs/02-policy-center/07-user-policy-design.md
docs/02-policy-center/08-external-api-reference.md
docs/README.md
```

后续补充：

```text
docs/02-policy-center/09-user-policy-api.md
```

仅供管理面视觉和交互评审：

```text
docs/doc_tmp/agent-gateway-admin-prototype.html
```

原型不连接后端，不参与 Maven 构建，也不应作为生产前端部署。

## 9. 重点冲突文件

### 9.1 AuthorizationDecisionService.java

如果主分支同时修改了工具授权决策，不能直接选择任意一侧版本。最终代码必须同时保留：

- 存量 Tool 绑定判断。
- Tool 人员策略判断。
- 存量 `authMode` 判断。
- Redis 对话授权判断。
- 存量审计和异常日志。

正确顺序参见第 5.3 节。

### 9.2 DecisionReason.java

必须保留主分支已有枚举，并增加：

```text
USER_TOOL_ACCESS_DENIED
```

不要用功能分支版本覆盖主分支新增的其他 reason。

### 9.3 policy-center-schema.sql

如果主分支同时新增了表，按表合并 SQL，不要整文件覆盖。

存量环境部署以独立的：

```text
sql/user-policy-schema.sql
```

为准。

### 9.4 策略中心文档

`02-api-contract.md`、`05-development-progress.md`、`06-admin-frontend-api.md` 和 `08-external-api-reference.md` 容易与同事文档产生冲突。

文档冲突可人工合并，但不能遗漏：

- `PUBLIC / RESTRICTED`。
- 3 个业务查询接口。
- 2 个人员策略管理接口。
- `USER_TOOL_ACCESS_DENIED`。
- Agent 权限不进入 Tool 运行时决策。

## 10. 构建与部署顺序

### 10.1 合入后构建

环境要求：

- Java 21。
- Maven。
- MySQL 8.x。
- Redis，供存量对话授权流程使用。

执行：

```powershell
mvn test
mvn clean package
```

### 10.2 测试环境部署

1. 备份 `policy_center` 数据库。
2. 执行 `sql/user-policy-schema.sql`。
3. 检查 4 张表及唯一索引。
4. 部署新的策略中心 JAR。
5. 确认以下环境变量仍正确：

```text
POLICY_CENTER_DB_URL
POLICY_CENTER_DB_USERNAME
POLICY_CENTER_DB_PASSWORD
POLICY_CENTER_REDIS_HOST
POLICY_CENTER_REDIS_PORT
POLICY_CENTER_REDIS_PASSWORD
```

6. 检查健康状态：

```http
GET /actuator/health
```

7. 执行接口验证：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\verify-user-policy.ps1 `
  -BaseUrl http://localhost:18080
```

8. 管理面联调 4 个管理接口。
9. MCP 网关验证 `USER_TOOL_ACCESS_DENIED` 处理。
10. 业务后端验证 Agent 访问和可访问列表接口。

### 10.3 生产部署

建议顺序：

1. 数据库增量建表。
2. 策略中心后端发布。
3. MCP 网关确认兼容新的拒绝 reason。
4. 业务后端接入 Agent 访问查询。
5. 管理面前端发布。

4 张新表为空时所有 Agent 和 Tool 人员策略默认为 `PUBLIC`，因此先部署数据库和后端不会主动限制现有用户。

## 11. 回滚说明

### 11.1 应用回滚

可以先回滚策略中心 JAR。新表为新增表，不会影响旧版本读取 `agent_tool_policy`。

### 11.2 数据库回滚

正常应用回滚不需要立即删除 4 张新表。保留数据更利于故障恢复。

如确认永久撤销功能，应先备份，再按依赖关系删除：

```sql
DROP TABLE IF EXISTS agent_tool_user_access_policy;
DROP TABLE IF EXISTS agent_user_access_policy;
DROP TABLE IF EXISTS agent_tool_user_policy;
DROP TABLE IF EXISTS agent_user_policy;
```

生产环境执行 DROP 前必须经过 DBA 审核。

## 12. 合入验收清单

- [ ] 已基于最新 `main` 创建合入分支。
- [ ] 已合入 `50f59a8`。
- [ ] 已合入 `cf2c71c`。
- [ ] `AuthorizationDecisionService` 冲突已人工复核。
- [ ] `DecisionReason` 保留主分支枚举并增加 `USER_TOOL_ACCESS_DENIED`。
- [ ] 未将 TMG 临时文档误算为用户策略生产代码。
- [ ] 已确认是否保留管理面假 HTML 原型。
- [ ] 已执行 `mvn test`。
- [ ] 已执行 `mvn clean package`。
- [ ] 存量环境只执行 `sql/user-policy-schema.sql`。
- [ ] 4 张人员策略表和索引创建成功。
- [ ] 已执行 `scripts/verify-user-policy.ps1`。
- [ ] 已验证 Agent 权限不影响 Tool 运行时决策。
- [ ] 已验证 RESTRICTED Tool 非白名单返回 `USER_TOOL_ACCESS_DENIED`。
- [ ] 已确认 Tool 解绑人员策略清理方案。
- [ ] 管理面、业务后端和 MCP 网关完成联调。
