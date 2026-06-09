# 权限策略中心开发进度

> 状态：当前开发进度基线
> 负责人：项目维护者
> 适用版本：V1
> 最后更新：2026-06-09
> 阅读顺序：02-05
> 文档职责：记录策略中心已经完成、正在开发和被阻塞的工作。功能要求以 [功能规格](01-policy-center-spec.md) 为准，接口要求以 [API 契约](02-api-contract.md) 为准。

## 当前概览

| 项目 | 当前状态 |
| --- | --- |
| 策略中心代码 | 进行中 |
| 对外接口 | 5 / 5 已实现；0 / 5 满足完整完成标准 |
| 内部功能 | 5 / 13 已完成 |
| 验收场景 | 运行时、管理保存、人在回路核心路径已有单元与 HTTP 测试覆盖；存储集成待验证 |
| 当前里程碑 | M1 进行中 |
| 当前代码基线 | Spring Boot 策略中心服务骨架、领域服务、REST 接口、MyBatis/Flyway、Redis 适配和自动化测试已进入仓库 |

本表只统计满足本文“完成标准”的项目，不使用主观百分比。

## 状态定义

| 状态 | 定义 |
| --- | --- |
| `未开始` | 尚无可验证的实现代码 |
| `进行中` | 已开始实现，但尚未满足全部完成标准 |
| `已完成` | 代码、测试、文档和关联验收场景全部满足完成标准 |
| `阻塞` | 无法继续实现，必须先完成外部决策或解除依赖 |

## 内部功能进度

| 编号 | 功能 | 交付内容 | 前置依赖 | 对应验收场景 | 状态 | 实现证据 / 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| F01 | Java 服务工程与配置体系 | 可启动的服务入口、环境配置、健康检查和 Maven 构建 | Spring Boot 3.4.9、Java 21 | 无 | 进行中 | `PolicyCenterApplication`、`application.yml`、Actuator、Maven 依赖已实现；待真实配置启动验证 |
| F02 | 领域枚举、请求响应和错误模型 | `AuthMode`、`Decision`、`Reason`、授权状态、API DTO 和错误结构 | F01、API 契约 | 场景 1-8、18-19 | 已完成 | `domain/`、`api/dto/`、`ErrorCode`、`GlobalExceptionHandler`；`mvn test` 24 tests passed |
| F03 | tokenId 解析与校验 | 解析 `agentId:userId:conversationId`，校验字段数量、非空和分隔符约束 | F02 | 场景 6、22 | 已完成 | `TokenId`、`TokenIdTest` |
| F04 | `agent_tool_policy` 数据库模型与仓储 | Flyway 表迁移、实体或记录模型、唯一约束、按 Agent 与工具查询 | F01、MySQL、Flyway | 场景 1-2、5、7、9-13、18-19 | 进行中 | `V1__create_agent_tool_policy.sql`、MyBatis Mapper 和 Repository 已实现；待 MySQL 集成验证 |
| F05 | Agent 工具策略整份覆盖 | 在单个事务内新增、更新和解绑，默认空标签为 `USER_AUTH_REQUIRED` | F02、F04 | 场景 9-13 | 进行中 | `ToolPolicyService`、`AdminToolPolicyController` 和测试已实现；待 MySQL 事务集成验证 |
| F06 | 三态授权决策引擎 | 按绑定、标签和当前对话授权返回 `ALLOW`、`AUTHORIZATION_REQUIRED` 或 `DENY` | F02-F04、F07 | 场景 1-8 | 已完成 | `AuthorizationDecisionServiceTest` 覆盖未绑定、无需授权、命中、未命中、非法 tokenId、DB/Redis 异常 |
| F07 | Redis 当前对话授权 | 操作 `authz:{tokenId}:{toolId}`，授权 Key TTL 7 天，本地单机开发并保留 Redis Cluster 配置 | F01、Spring Data Redis + Lettuce | 场景 3-4、8、14-15、20-22 | 进行中 | `RedisConversationAuthorizationStore` 已实现；待本地 Redis 集成验证 |
| F08 | 用户授权确认与幂等写入 | 重新检查工具策略，幂等写入授权，拒绝未绑定或无需授权工具 | F03-F05、F07 | 场景 14-15、18-19 | 进行中 | `ConversationAuthorizationServiceTest` 覆盖确认、未绑定、无需授权；待 Redis 集成验证 |
| F09 | 授权状态查询 | 查询当前对话授权，区分 `AUTHORIZED` 与 `NOT_AUTHORIZED` | F03、F07 | 场景 16-17 | 进行中 | 服务和 Controller 已实现并测试；待 Redis 集成验证 |
| F10 | 对话授权清理 | 使用 `SCAN MATCH authz:{tokenId}:*` 分批清理全部工具授权，支持重复清理 | F03、F07 | 场景 20-21 | 进行中 | cleanup 接口和 Redis SCAN 适配已实现；待 Redis 集成验证 |
| F11 | 统一异常处理与 fail-closed | 参数错误、数据库异常、Redis 异常和内部错误的统一映射 | F02、F04、F07 | 场景 6-8、12、18-21、23 | 已完成 | `GlobalExceptionHandler`、服务 fail-closed 测试、HTTP 错误响应测试 |
| F12 | 审计日志与 traceId | 记录配置、决策、确认、查询和清理事件，不记录敏感凭证 | F01、F02、接口认证与 traceId 方案 | 场景 24 | 已完成 | `AuditLogger`、`Slf4jAuditLogger`，服务审计测试覆盖配置、决策、确认、查询和清理 |
| F13 | 自动化测试体系 | 单元测试、数据库与 Redis 集成测试、接口测试和完整验收回归 | F01-F12 | 场景 1-24 | 进行中 | 当前 24 个单元/HTTP 测试通过；待数据库与 Redis 集成测试 |

## 对外接口进度

接口交付项统一检查：

1. 请求和响应模型
2. 参数校验
3. Controller 接入
4. 领域服务实现
5. 数据库或 Redis 集成
6. 错误码映射
7. 审计事件
8. 自动化测试
9. API 文档一致性

### I01 运行时授权决策

```http
POST /internal/authorization-decisions
```

| 交付项 | 状态 | 实现证据 / 备注 |
| --- | --- | --- |
| 请求和响应模型 | 已完成 | `AuthorizationDecisionRequest`、`AuthorizationDecision` |
| 参数校验 | 已完成 | `@NotBlank` + HTTP 400 测试 |
| Controller 接入 | 已完成 | `AuthorizationDecisionController` |
| 领域服务实现 | 已完成 | `AuthorizationDecisionService` |
| 数据库或 Redis 集成 | 进行中 | MyBatis/Redis 适配已实现，待真实存储集成验证 |
| 错误码映射 | 已完成 | `INVALID_TOKEN_ID`、`POLICY_STORE_UNAVAILABLE`、`AUTHORIZATION_STORE_UNAVAILABLE` 等 fail-closed 测试 |
| 审计事件 | 已完成 | `AUTHORIZATION_DECISION` 审计事件 |
| 自动化测试 | 已完成 | `AuthorizationDecisionServiceTest`、`AuthorizationDecisionControllerTest` |
| API 文档一致性 | 已完成 | 响应结构与 API 契约一致 |

### I02 管理员整份保存工具策略

```http
PUT /admin/agents/{agentId}/tool-policies
```

| 交付项 | 状态 | 实现证据 / 备注 |
| --- | --- | --- |
| 请求和响应模型 | 已完成 | `SaveToolPoliciesRequest`、`ToolPolicySaveResult` |
| 参数校验 | 已完成 | 空字段、非法枚举、重复 toolId 由 Validation/Service 拦截 |
| Controller 接入 | 已完成 | `AdminToolPolicyController` |
| 领域服务实现 | 已完成 | `ToolPolicyService` |
| 数据库或 Redis 集成 | 进行中 | MyBatis/Flyway 代码已实现，待 MySQL 事务验证 |
| 错误码映射 | 已完成 | `INVALID_REQUEST`、`POLICY_STORE_UNAVAILABLE` |
| 审计事件 | 已完成 | `TOOL_POLICY_REPLACED` 审计事件 |
| 自动化测试 | 已完成 | `ToolPolicyServiceTest`、`AdminToolPolicyControllerTest` |
| API 文档一致性 | 已完成 | 保存请求只提交已绑定工具 |

### I03 确认当前对话授权

```http
POST /internal/conversation-authorizations
```

| 交付项 | 状态 | 实现证据 / 备注 |
| --- | --- | --- |
| 请求和响应模型 | 已完成 | `ConversationAuthorizationRequest`、`ConversationAuthorizationResult` |
| 参数校验 | 已完成 | tokenId/toolId 必填校验 |
| Controller 接入 | 已完成 | `ConversationAuthorizationController` |
| 领域服务实现 | 已完成 | `ConversationAuthorizationService.authorize` |
| 数据库或 Redis 集成 | 进行中 | Redis 写入 TTL 代码已实现，待真实 Redis 验证 |
| 错误码映射 | 已完成 | `TOOL_NOT_BOUND`、`AUTHORIZATION_NOT_REQUIRED`、存储异常 |
| 审计事件 | 已完成 | `CONVERSATION_AUTHORIZED` 审计事件 |
| 自动化测试 | 已完成 | `ConversationAuthorizationServiceTest`、`ConversationAuthorizationControllerTest` |
| API 文档一致性 | 已完成 | V1 仅提交 `tokenId + toolId` |

### I04 查询当前对话授权状态

```http
POST /internal/conversation-authorizations/status
```

| 交付项 | 状态 | 实现证据 / 备注 |
| --- | --- | --- |
| 请求和响应模型 | 已完成 | `ConversationAuthorizationRequest`、`ConversationAuthorizationStatusResponse` |
| 参数校验 | 已完成 | tokenId/toolId 必填校验 |
| Controller 接入 | 已完成 | `ConversationAuthorizationController` |
| 领域服务实现 | 已完成 | `ConversationAuthorizationService.status` |
| 数据库或 Redis 集成 | 进行中 | Redis 查询代码已实现，待真实 Redis 验证 |
| 错误码映射 | 已完成 | Redis 异常返回 `AUTHORIZATION_STORE_UNAVAILABLE` |
| 审计事件 | 已完成 | `AUTHORIZATION_STATUS_QUERIED` 审计事件 |
| 自动化测试 | 已完成 | `ConversationAuthorizationServiceTest` |
| API 文档一致性 | 已完成 | 查询状态不替代 MCP 网关完整鉴权 |

### I05 清理当前对话授权

```http
POST /internal/conversation-authorizations/cleanup
```

| 交付项 | 状态 | 实现证据 / 备注 |
| --- | --- | --- |
| 请求和响应模型 | 已完成 | `CleanupAuthorizationRequest`、`CleanupResult` |
| 参数校验 | 已完成 | tokenId 必填校验 |
| Controller 接入 | 已完成 | `ConversationAuthorizationController` |
| 领域服务实现 | 已完成 | `ConversationAuthorizationService.cleanup` |
| 数据库或 Redis 集成 | 进行中 | `SCAN MATCH authz:{tokenId}:*` 代码已实现，待真实 Redis 验证 |
| 错误码映射 | 已完成 | Redis 清理失败返回 `AUTHORIZATION_STORE_UNAVAILABLE` |
| 审计事件 | 已完成 | `CONVERSATION_AUTHORIZATION_CLEANED` 审计事件 |
| 自动化测试 | 已完成 | `ConversationAuthorizationControllerTest` |
| API 文档一致性 | 已完成 | 不使用 Redis `KEYS` |

## 阻塞项

| 编号 | 待确认事项 | 阻塞范围 | 状态 | 解除条件 |
| --- | --- | --- | --- | --- |
| B01 | Spring Boot 及依赖版本 | F01-F13、I01-I05 | 已解除 | Spring Boot 3.4.9 + Java 21 |
| B02 | 数据库和迁移工具 | F04-F06、F08、I01-I03 | 已解除 | MySQL + Flyway |
| B03 | Redis 客户端和部署模式 | F06-F11、I01、I03-I05 | 已解除 | Spring Data Redis + Lettuce，本地单机开发，生产保留 Cluster 配置 |
| B04 | 内部接口认证方式 | F12、I01、I03-I05 | 已解除 | V1 暂不实现接口认证 |
| B05 | 授权记录物理安全 TTL | F07-F10 | 已解除 | 授权 Key TTL 7 天 |
| B06 | 对话结束与迟到授权并发处理 | F08、F10、I03、I05 | 已解除 | V1 信任业务后端不会迟到确认，不维护关闭状态 |

阻塞状态只影响表中列出的范围；不依赖该决策的设计和实现可以继续推进。

## 里程碑

| 里程碑 | 完成条件 | 状态 | 实现证据 / 备注 |
| --- | --- | --- | --- |
| M1 | 服务可启动，基础配置、领域契约、tokenId 解析和异常框架完成 | 进行中 | Maven 编译和测试通过；待真实配置启动验证 |
| M2 | 管理员工具策略整份保存可用，场景 9-13 通过 | 进行中 | API、服务、MyBatis 代码和 HTTP 测试已完成；待 MySQL 集成验证 |
| M3 | 运行时三态授权决策可用，场景 1-8 通过 | 已完成 | `AuthorizationDecisionServiceTest` 覆盖核心决策与 fail-closed |
| M4 | 授权确认、状态查询和对话清理接口完整，场景 14-21 通过 | 进行中 | API、服务、Redis 适配和 HTTP 测试已完成；待 Redis 集成验证 |
| M5 | 24 个验收场景全部通过，Maven 构建成功，V1 文档与实现一致 | 未开始 | 无实现代码 |

## 完成标准

功能、接口交付项或里程碑只有同时满足以下条件，才可以标记为 `已完成`：

- 实现代码已进入仓库。
- 对应自动化测试通过。
- 关联验收场景通过。
- API 契约、数据模型和功能规格不存在冲突。
- Maven 构建通过。
- 实现文件、测试文件或提交记录已经写入“实现证据 / 备注”。

状态更新规则：

- 开始编写实现代码时，从 `未开始` 更新为 `进行中`。
- 因未决事项无法继续时，更新为 `阻塞` 并关联阻塞项编号。
- 只完成代码但缺少测试或文档核对时，仍保持 `进行中`。
- 删除或改变既有能力时，必须重新评估关联功能、接口、场景和里程碑状态。

## 变更记录

| 日期 | 变更项 | 状态变化 | 验证结果 |
| --- | --- | --- | --- |
| 2026-06-09 | 建立策略中心开发进度基线 | F01-F13、I01-I05、M1-M5 初始化为未开始；B01-B06 初始化为阻塞 | 确认 `src` 中尚无策略中心实现或测试文件 |
| 2026-06-09 | 确认后端实现基础选型 | B01-B06 解除，M1/F01 进入进行中 | Spring Boot 3.4.9、MySQL/Flyway、MyBatis XML、Redis/Lettuce、7 天 TTL、SCAN cleanup 已写入文档 |
| 2026-06-09 | 完成策略中心 V1 后端初始实现 | F02、F03、F06、F11、F12 标记已完成；F01、F04、F05、F07-F10、F13 进行中 | `mvn test` 通过，24 tests，0 failures |
