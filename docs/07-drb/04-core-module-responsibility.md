# 三个核心模块分工

> 状态：方案讲解材料
> 负责人：项目维护者
> 适用版本：V1
> 最后更新：2026-06-16
> 阅读顺序：07-04
> 文档职责：用一个表格说明 Agent 网关、策略中心、MCP 网关三个核心模块的定位和交付特性。

| 模块 | 模块定位 | 交付特性 |
| --- | --- | --- |
| Agent 网关 | 后端到 Agent 的统一接入与代理层 | 统一代理入口；Agent 动态路由；`tokenId` 生成；`tokenId` 透传；Cookie 隔离；Cookie 剥离；SSE 流式代理；请求链路审计 |
| 策略中心 | 动态授权决策层 | 工具绑定配置；工具标签配置；人员策略配置；三态授权决策；当前对话授权；授权状态查询；授权记录清理；fail-closed；决策审计日志 |
| MCP 网关 | MCP 工具调用入口和最终放行点 | 工具调用入口；工具路由管理；调用前鉴权；`ALLOW` 后放行；Cookie 按需获取；凭证受控注入；未授权拦截；拒绝结果返回 |

## 相关文档

- [Agent 动态授权安全整体方案](00-agent-security-overall-solution.md)
- [策略中心决策模型](03-policy-center-decision-model.md)
- [Agent 网关方案设计](02-agent-gateway-solution-design.md)
