# Agent 动态授权项目总体架构

> 状态：当前架构基线
> 负责人：项目维护者
> 适用版本：V1
> 最后更新：2026-06-09
> 阅读顺序：01
> 文档职责：描述系统参与者、跨模块调用链和职责边界。策略中心内部规则以 [策略中心功能规格](../02-policy-center/01-policy-center-spec.md) 为准。

## 架构范围

当前版本关注以下主链：

```text
用户 -> 业务后端 -> Agent 网关 -> Agent -> MCP 网关
     -> 权限策略中心 -> MCP Server -> 业务 API
```

IAM、IDaaS 等认证系统可以作为 Agent 网关的内部依赖存在，但不属于当前架构边界。

## 总体架构图

```mermaid
flowchart LR
    subgraph access["Agent 接入平面"]
        direction LR
        user["用户"]
        backend["业务后端<br/>业务会话与授权页面"]
        agentGateway["Agent 网关<br/>请求接入与 Agent 路由<br/>tokenId 生成与 Cookie 隔离保存"]
        agent["Agent<br/>任务执行与 MCP 调用<br/>授权挂起与恢复"]

        user -->|"1. 业务请求"| backend
        backend -->|"2. 用户、对话、请求与 Cookie"| agentGateway
        agentGateway -->|"3. 请求与 tokenId"| agent
    end

    subgraph mcpAccess["MCP 接入平面"]
        mcpGateway["MCP 网关<br/>工具路由与授权流程编排<br/>Cookie 获取与注入"]
    end

    subgraph control["动态授权平面"]
        direction TB
        admin["用户管理员<br/>配置 Agent-工具策略"]
        policyCenter["权限策略中心<br/>策略管理与动态授权决策"]
        policyDb[("策略配置数据库")]
        grantCache[("当前对话授权缓存")]

        admin -->|"配置工具策略"| policyCenter
        policyCenter --> policyDb
        policyCenter --> grantCache
    end

    subgraph resource["MCP 资源平面"]
        direction LR
        mcpServer["MCP Server<br/>工具实现与协议适配"]
        businessApi["企业业务系统 / 工具 API"]
        mcpServer -->|"10. 受控调用业务 API"| businessApi
    end

    subgraph operations["审计与可观测平面"]
        audit["统一审计与可观测性"]
    end

    agent -->|"4. tokenId + 工具调用"| mcpGateway
    mcpGateway -->|"5. tokenId + toolId"| policyCenter
    policyCenter -->|"6. 授权决策"| mcpGateway
    mcpGateway -->|"7. 允许后按 tokenId 获取 Cookie"| agentGateway
    agentGateway -->|"8. 返回关联 Cookie"| mcpGateway
    mcpGateway -->|"9. 注入 Cookie 并调用工具"| mcpServer

    agentGateway -.-> audit
    mcpGateway -.-> audit
    policyCenter -.-> audit
    mcpServer -.-> audit

    classDef actor fill:#f5f5f5,stroke:#666,color:#222,stroke-width:1.5px;
    classDef gateway fill:#e7f8f7,stroke:#168c8c,color:#075f60,stroke-width:1.5px;
    classDef policy fill:#f3edff,stroke:#7650b5,color:#4e2b87,stroke-width:1.5px;
    classDef store fill:#fffbea,stroke:#bc921b,color:#654c00,stroke-width:1.5px;
    classDef service fill:#fff2e8,stroke:#ed7417,color:#9a4108,stroke-width:1.5px;
    classDef ops fill:#edf7ed,stroke:#438a45,color:#245426,stroke-width:1.5px;

    class user,backend actor;
    class agentGateway,mcpGateway gateway;
    class admin,policyCenter policy;
    class policyDb,grantCache store;
    class agent,mcpServer,businessApi service;
    class audit ops;
```

## 核心调用时序图

该图表示目标工具需要用户授权，且当前对话授权已经存在的正常调用路径。策略判断规则见 [授权决策](../02-policy-center/01-policy-center-spec.md#授权决策)。

```mermaid
sequenceDiagram
    autonumber off
    actor User as 用户
    participant Backend as 业务后端
    participant AGW as Agent 网关
    participant Agent as Agent
    participant MGW as MCP 网关
    participant PDP as 权限策略中心
    participant MCP as MCP Server
    participant API as 业务 API

    User->>Backend: 1. 发起业务请求
    Backend->>AGW: 2. 提交 userId、conversationId、Agent 请求与 Cookie
    AGW->>AGW: 获取全局唯一 agentId
    AGW->>AGW: 生成 tokenId = agentId:userId:conversationId
    AGW->>AGW: 按 tokenId 关联保存 Cookie
    AGW->>Agent: 3. 转发请求与 tokenId

    Agent->>MGW: 4. 携带 tokenId 调用 MCP
    MGW->>PDP: 5. 提交 tokenId 与 toolId
    PDP->>PDP: 计算授权查询键 authz:{tokenId}:{toolId}
    PDP->>PDP: 对 Redis 执行 EXISTS 查询
    PDP->>PDP: EXISTS = 1，当前对话已授权
    PDP-->>MGW: 6. 允许
    MGW->>AGW: 7. 按 tokenId 请求关联 Cookie
    AGW-->>MGW: 8. 返回 Cookie
    MGW->>MCP: 9. 注入 Cookie 并调用 MCP 工具
    MCP->>API: 10. 受控调用业务 API
    API-->>MCP: 业务响应
    MCP-->>MGW: 工具执行结果
    MGW-->>Agent: 返回脱敏后的调用结果
    Agent-->>AGW: 返回任务结果
    AGW-->>Backend: 返回 Agent 执行结果
    Backend-->>User: 返回最终结果

    Note over AGW,MCP: tokenId 生成与传递、授权决策和工具调用均写入统一审计链路
```

## 未授权人在回路授权时序图

该图表示目标工具需要用户授权，但当前对话授权不存在时的挂起、确认和恢复路径。

```mermaid
sequenceDiagram
    autonumber off
    actor User as 用户
    participant Backend as 业务后端
    participant AGW as Agent 网关
    participant Agent as Agent
    participant MGW as MCP 网关
    participant PDP as 权限策略中心
    participant MCP as MCP Server

    Agent->>MGW: 1. 携带 tokenId 调用 MCP 工具
    MGW->>PDP: 2. 提交 tokenId 与 toolId
    PDP->>PDP: 查询 authz:{tokenId}:{toolId}
    PDP->>PDP: Redis EXISTS = 0
    PDP-->>MGW: 3. 返回未授权
    MGW-->>Agent: 4. 返回未授权状态与 toolId
    Agent->>Agent: 5. 保存执行检查点并挂起

    par 用户确认授权
        Agent->>AGW: 6. 提交 tokenId、toolId 与授权请求
        AGW->>Backend: 7. 转发授权请求
        Backend-->>User: 8. 渲染 1 分钟有效的授权页面<br/>允许本次对话调用该工具
        User->>Backend: 9. 同意本次对话授权
        Backend->>PDP: 10. 提交 tokenId 与 toolId
        PDP->>PDP: 写入 authz:{tokenId}:{toolId}
        PDP-->>Backend: 11. 授权写入成功
    and Agent 轮询授权状态
        loop 每 2 秒一次，最长 1 分钟
            Agent->>PDP: 查询 tokenId 与 toolId 授权状态
            PDP-->>Agent: 未授权 / 已授权
        end
    end

    alt 1 分钟内查询到已授权
        Agent->>Agent: 12. 恢复执行检查点
        Agent->>MGW: 13. 重新携带 tokenId 调用 MCP 工具
        MGW->>PDP: 14. 重新提交 tokenId 与 toolId
        PDP->>PDP: Redis EXISTS = 1
        PDP-->>MGW: 15. 返回允许
        MGW->>AGW: 16. 按 tokenId 请求关联 Cookie
        AGW-->>MGW: 17. 返回 Cookie
        MGW->>MCP: 18. 注入 Cookie 并调用 MCP 工具
        MCP-->>MGW: 返回工具执行结果
        MGW-->>Agent: 返回调用结果
    else 轮询超过 1 分钟仍未授权
        Agent->>Agent: 12. 结束挂起的执行检查点
        Agent->>AGW: 返回用户未授权
        AGW->>Backend: 返回授权超时结果
        Backend-->>User: 提示授权未完成
    end

    Note over Backend,PDP: 授权请求、用户确认、Redis 写入、轮询与检查点恢复均写入审计日志
```

## 组件边界

| 组件 | 核心职责 | 明确边界 |
| --- | --- | --- |
| 业务后端 | 承接业务请求和会话、提供用户与对话上下文、展示授权页面 | 不生成 tokenId，不执行策略判断 |
| Agent 网关 | Agent 路由、生成 tokenId、隔离保存 Cookie | 不执行工具授权决策 |
| Agent | 执行任务、调用 MCP、未授权时挂起和恢复 | 不接触 Cookie，不自行判断授权 |
| MCP 网关 | 工具接入、调用策略中心、依据决策控制工具调用 | 不制定策略，不长期保存 Cookie |
| 权限策略中心 | 管理 Agent-工具策略、处理当前对话授权、返回授权决策 | 不生成 tokenId，不调用 MCP 工具 |
| MCP Server | 实现工具并访问业务 API | 不管理用户授权策略 |

## 跨模块约束

- `tokenId` 当前由 Agent 网关按 `agentId:userId:conversationId` 生成，仅作为授权上下文关联标识。
- Agent 只透传 tokenId，不能接触 Cookie、长期 Token、密钥或其他原始业务凭证。
- MCP 网关只有在权限策略中心明确返回 `ALLOW` 后，才能获取 Cookie 并调用 MCP Server。
- 当前版本只支持本次对话授权；跨对话授权属于未来演进能力。
- 工具授权判断、配置变更和最终调用必须能够通过 tokenId 关联审计。

## 相关文档

- [文档导航](../README.md)
- [策略中心功能规格](../02-policy-center/01-policy-center-spec.md)
- [策略中心 API 契约](../02-policy-center/02-api-contract.md)
- [策略中心数据模型](../02-policy-center/03-data-model.md)
- [策略中心验收场景](../02-policy-center/04-acceptance-scenarios.md)
- [未来授权能力演进](../04-future/01-future-authorization-evolution.md)
