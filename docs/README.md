# Agent 动态授权项目文档

> 状态：当前文档入口
> 负责人：项目维护者
> 适用版本：V1
> 最后更新：2026-06-16
> 阅读顺序：00（文档入口）

## 目录顺序

```text
docs/
├── README.md
├── 01-architecture/
│   └── 01-project-overall-architecture.md
├── 02-policy-center/
│   ├── 01-policy-center-spec.md
│   ├── 02-api-contract.md
│   ├── 03-data-model.md
│   ├── 04-acceptance-scenarios.md
│   ├── 05-development-progress.md
│   ├── 06-admin-frontend-api.md
│   ├── 07-user-policy-design.md
│   ├── 08-external-api-reference.md
│   ├── 09-user-policy-api.md
│   └── 10-user-policy-merge-deployment-guide.md
├── maven-version-reference.md
├── 03-decisions/
│   ├── README.md
│   ├── ADR-001-plaintext-token-id.md
│   ├── ADR-002-conversation-scoped-authorization.md
│   └── ADR-003-agent-tool-policy-source.md
├── 04-future/
│   └── 01-future-authorization-evolution.md
├── 05-integration/
│   ├── 01-developer-access-user-journey.md
│   ├── 02-developer-integration-api-guide.md
│   ├── 03-finance-agent-authorization-apis.md
│   └── 99-reference-business-backend-agent-integration-guide.md
├── 06-agent-gateway/
│   ├── 01-agent-gateway-project-overall-architecture.md
│   └── 02-agent-gateway-api-reference.md
├── 07-drb/
│   ├── 00-agent-security-overall-solution.md
│   ├── 01-policy-center-solution-design.md
│   ├── 02-agent-gateway-solution-design.md
│   ├── 03-policy-center-decision-model.md
│   └── 04-core-module-responsibility.md
└── 99-assets/
    └── agent-auth-mcp-architecture.png
```

`README.md` 保留标准名称，作为 GitHub 自动展示的入口；其他文件和目录按数字前缀排序。`99-assets/` 只存放图片等非阅读材料。

## 阅读顺序

项目开发和业务接入前按以下顺序读取：

1. [项目总体架构](01-architecture/01-project-overall-architecture.md)：理解参与者、跨模块调用链和职责边界。
2. [Agent 动态授权安全整体方案（DRB）](07-drb/00-agent-security-overall-solution.md)：面向 CTO/DRB 先理解全局价值、主链路和模块分工。
3. [三个核心模块分工](07-drb/04-core-module-responsibility.md)：快速理解 Agent 网关、策略中心和 MCP 网关的定位、职责和特性。
4. [权限策略中心方案设计（DRB）](07-drb/01-policy-center-solution-design.md)：从黑盒到白盒理解策略中心决策、存储和可靠性设计。
5. [策略中心决策模型](07-drb/03-policy-center-decision-model.md)：聚焦理解 MCP 网关提交 `tokenId + toolId` 后策略中心内部如何决策。
6. [Agent 网关方案设计（DRB）](07-drb/02-agent-gateway-solution-design.md)：从黑盒到白盒理解 H2A 代理、动态路由和 Cookie 隔离。
7. [策略中心功能规格](02-policy-center/01-policy-center-spec.md)：理解当前版本必须实现的行为。
8. [策略中心 API 契约](02-policy-center/02-api-contract.md)：确认调用方、请求、响应和错误语义。
9. [策略中心数据模型](02-policy-center/03-data-model.md)：确认数据库、Redis、事务和清理规则。
10. [策略中心验收场景](02-policy-center/04-acceptance-scenarios.md)：据此实现自动化测试并验收。
11. [策略中心开发进度](02-policy-center/05-development-progress.md)：确认已经实现、正在开发和被阻塞的工作。
12. [管理面前端接口](02-policy-center/06-admin-frontend-api.md)：交付前端同事联调管理页面。
13. [人的权限策略配置方案](02-policy-center/07-user-policy-design.md)：理解 Agent 访问策略和 Tool 用户策略的边界。
14. [策略中心对外接口参考](02-policy-center/08-external-api-reference.md)：按调用方查看当前全部接口及可直接联调的示例数据。
15. [开发者接入用户旅程](05-integration/01-developer-access-user-journey.md)：用于宣讲业务方如何从管理面配置到运行面调用理解完整接入旅程。
16. [开发人员详细接入文档](05-integration/02-developer-integration-api-guide.md)：交付开发人员按接口、示例和联调步骤完成标准接入。
17. [财经 Agent 授权预检与批量授权接口](05-integration/03-finance-agent-authorization-apis.md)：交付财经 Agent 定制接入。
18. [Agent 网关 API 参考](06-agent-gateway/02-agent-gateway-api-reference.md)：查看 Agent 网关运行面接口字段。
19. [Agent 网关 H2A 代理设计](06-agent-gateway/01-agent-gateway-project-overall-architecture.md)：理解 Agent 网关代理流量、tokenId 生成和 Cookie 隔离保存。
20. [架构决策记录](03-decisions/README.md)：了解已经确认、不应在编码时随意改变的设计。
21. [业务后端与业务 Agent 接入指南（参考材料）](05-integration/99-reference-business-backend-agent-integration-guide.md)：保留早期完整接入说明，作为参考材料阅读。

[未来授权能力演进](04-future/01-future-authorization-evolution.md) 只用于评估扩展性，不属于 V1 实现和验收范围。

## 唯一事实来源

| 内容 | 权威文档 |
| --- | --- |
| 系统参与者、调用主链、模块边界 | [项目总体架构](01-architecture/01-project-overall-architecture.md) |
| CTO/DRB 整体方案讲解 | [Agent 动态授权安全整体方案（DRB）](07-drb/00-agent-security-overall-solution.md) |
| 三个核心模块定位、职责和特性 | [三个核心模块分工](07-drb/04-core-module-responsibility.md) |
| 策略中心 DRB 方案设计 | [权限策略中心方案设计（DRB）](07-drb/01-policy-center-solution-design.md) |
| 策略中心运行时决策模型讲解 | [策略中心决策模型](07-drb/03-policy-center-decision-model.md) |
| Agent 网关 DRB 方案设计 | [Agent 网关方案设计（DRB）](07-drb/02-agent-gateway-solution-design.md) |
| 授权决策、人在回路、失败行为 | [策略中心功能规格](02-policy-center/01-policy-center-spec.md) |
| HTTP 接口字段、响应、错误码 | [策略中心 API 契约](02-policy-center/02-api-contract.md) |
| 数据库表、Redis Key、事务和清理 | [策略中心数据模型](02-policy-center/03-data-model.md) |
| 可验证行为和测试场景 | [策略中心验收场景](02-policy-center/04-acceptance-scenarios.md) |
| 当前实现状态、阻塞项和里程碑 | [策略中心开发进度](02-policy-center/05-development-progress.md) |
| 管理面前端页面联调接口 | [管理面前端接口](02-policy-center/06-admin-frontend-api.md) |
| 人的权限策略配置与运行时工具用户策略 | [人的权限策略配置方案](02-policy-center/07-user-policy-design.md) |
| 策略中心全部对外接口与联调示例 | [策略中心对外接口参考](02-policy-center/08-external-api-reference.md) |
| 业务宣讲和接入旅程理解 | [开发者接入用户旅程](05-integration/01-developer-access-user-journey.md) |
| 开发人员标准接口接入与联调步骤 | [开发人员详细接入文档](05-integration/02-developer-integration-api-guide.md) |
| 财经 Agent 定制接入接口 | [财经 Agent 授权预检与批量授权接口](05-integration/03-finance-agent-authorization-apis.md) |
| 业务后端与业务 Agent 接入参考材料 | [业务后端与业务 Agent 接入指南（参考材料）](05-integration/99-reference-business-backend-agent-integration-guide.md) |
| Agent 网关代理流量、tokenId 和 Cookie 隔离 | [Agent 网关 H2A 代理设计](06-agent-gateway/01-agent-gateway-project-overall-architecture.md) |
| 已接受的重要设计选择 | [架构决策记录](03-decisions/README.md) |
| 未进入当前版本的方案 | [未来授权能力演进](04-future/01-future-authorization-evolution.md) |

发生冲突时，应先修正文档冲突，再编码；不得自行选择较宽松的授权行为。

## 当前开发状态

- 仓库当前是 Java 21 Maven 骨架，策略中心后端开始进入实现阶段。
- 服务框架已确认：Spring Boot 3.4.9 + Java 21。
- 数据库方案已确认：MySQL；新环境执行 [policy-center-schema.sql](../sql/policy-center-schema.sql)，已完成基础建表的存量环境执行 [user-policy-schema.sql](../sql/user-policy-schema.sql) 增量创建人员策略表。
- 数据访问方式已确认：MyBatis + XML Mapper。
- Redis 客户端已确认：Spring Data Redis + Lettuce，本地单机开发，生产保留 Redis Cluster 配置能力。
- V1 暂不实现接口认证，生产接入前需要补充内部调用认证。
- 具体进度以 [策略中心开发进度](02-policy-center/05-development-progress.md) 为准。
- 服务启动后可手动运行 `scripts/verify-policy-center.ps1` 验收 MySQL、Redis 和基础策略接口，也可运行 `scripts/verify-user-policy.ps1` 查看人员策略接口的请求与响应；这些脚本不会随服务自动执行。

## 当前版本边界

V1 包含：

- Agent-工具绑定及 `NO_AUTH_REQUIRED`、`USER_AUTH_REQUIRED` 标签。
- 基于 `tokenId + toolId` 的三态授权决策。
- 当前对话用户授权、轮询查询和对话结束清理。
- 数据库和 Redis 异常时默认拒绝。

V1 暂不提供，相关能力开发中：

- 跨对话 7 天或 30 天授权。
- 用户授权列表和主动撤销。
- 不透明随机 tokenId。
- Agent-工具策略 Redis 缓存。
- Cookie 的保存或注入实现。

## 文档维护规则

- 当前行为写入总体架构或 `02-policy-center/`，未来候选只写入 `04-future/`。
- 一个规则只在一份权威文档中完整定义，其他文档通过链接引用。
- 功能规格描述“应该实现什么”，开发进度描述“当前已经实现到哪里”，进度文档不得重新定义功能规则。
- 枚举、字段名、Redis Key 和错误码必须与代码保持一致。
- 尚未确认且会影响实现的内容标为 `TBD`，编码前集中确认。
- 代码实现改变外部行为时，必须同步更新 API、数据模型和验收场景。
