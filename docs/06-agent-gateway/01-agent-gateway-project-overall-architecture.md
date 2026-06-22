# H2A 代理功能设计文档

## 1. 概述

H2A（Human-to-Agent）代理是 Agent Gateway 中的核心模块，负责处理人机交互场景下的 SSE 长连接代理。它通过 `/h2a/` 路径前缀识别请求，独立完成 Cookie→Token 委托验证、动态路由、SSE 流式转发，与 `/a2a/`（Agent-to-Agent）机机交互路径完全隔离。

**核心能力**：
- Cookie→Token 委托验证（Cookie 缓存 Redis，转发时替换为 tokenId）
- 基于 agentId 的动态路由（查询 AgentCard 获取 agent URL）
- SSE 长连接双向透传
- HTTPS 出站支持
- 策略模块委托事件异步通知

---

## 2. 整体架构

### 2.1 Netty Pipeline 结构

```
HttpServerCodec → HttpObjectAggregator → [AuthHandler] → H2ARouteHandler → AdapterHandler → MsgRouteHandler
```

| 路径 | 流转 |
|------|------|
| `/h2a/` | AuthHandler 直接放行 → H2ARouteHandler 拦截并完成全部处理，请求终止 |

### 2.2 组件关系图

```
Web Copilot (浏览器)
    │
    │ POST /h2a/{agentId}/{userPath}
    │ Cookie + sessionId + userId
    ▼
┌─────────────────────────────────────────────────┐
│                 Agent Gateway                    │
│                                                  │
│  AuthHandler ──→ H2ARouteHandler                │
│                    │                             │
│                    ├─ 1. 路径解析 (agentId+userPath) │
│                    ├─ 2. AgentCardService 查询 agentUrl │
│                    ├─ 3. 策略模块异步通知          │
│                    ├─ 4. Token 颁发/刷新 (Redis)  │
│                    ├─ 5. Cookie→tokenId Header 替换 │
│                    └─ 6. Netty Bootstrap 出站连接  │
│                           │                      │
│                    H2ABackendHandler             │
│                    H2AResponseRelay              │
│                           │                      │
└───────────────────────────┼──────────────────────┘
                            │ SSE 流式透传
                            ▼
                    Agent 服务 (动态路由)
```

---

## 3. 请求处理流程

### 3.1 完整时序

```
Web后端                    Agent Gateway                     Agent
  │                            │                               │
  │─ POST /h2a/{agentId}/path─→│                               │
  │  Cookie, sessionId, userId │                               │
  │                            │─ AuthHandler: /h2a/放行       │
  │                            │─ 正则匹配 agentId + userPath  │
  │                            │─ 校验 sessionId(缺失则关闭)    │
  │                            │─ AgentCardService查agentUrl   │
  │                            │  (失败→500 SSE错误事件)        │
  │                            │─ 异步通知策略模块              │
  │                            │─ 拼接tokenId, 查Redis         │
  │                            │  命中→刷新issueTime/expiryTime│
  │                            │  未命中→新建Token, TTL=1h     │
  │                            │─ Header: 移除Cookie, 注入tokenId│
  │                            │─ Netty出站连接到agentUrl+userPath│
  │                            │──────────────────────────────→│
  │                            │  (HTTPS时自动注入SslContext)   │
  │                            │←───── SSE 响应流 ────────────│
  │←── SSE 响应流透传 ────────│                               │
  │←── 连接关闭 ──────────────│                               │
```

### 3.2 路径解析规则

正则：`^/h2a/([^/]+)(/.*)?$`

| 请求 | agentId | userPath | agent.url | 转发目标 |
|------|---------|----------|-----------|----------|
| `/h2a/113` | "113" | "" | `http://www.eft.com` | `http://www.eft.com` |
| `/h2a/113/abc` | "113" | "/abc" | `http://www.eft.com` | `http://www.eft.com/abc` |
| `/h2a/113/abc?x=1` | "113" | "/abc?x=1" | `http://www.eft.com/api/v1` | `http://www.eft.com/api/v1/abc?x=1` |
| `/h2a/113/a/b/c` | "113" | "/a/b/c" | `http://11.51.4.19:19810` | `http://11.51.4.19:19810/a/b/c` |

转发 URI 拼接逻辑：`agentUrl去尾斜杠 + userPath`，支持保留 query string 和 fragment。

---

## 4. Token 委托验证机制

### 4.1 Token 结构

```json
{
  "tokenId": "agentId:userId:sessionId",
  "cookie": "IDaaS_SSO=xxx",
  "issueTime": "2026-06-04T10:00:00Z",
  "expiryTime": "2026-06-04T11:00:00Z"
}
```

- **tokenId 格式**：`{agentId}:{userId}:{sessionId}`，通过 `:` 分割还原
- **Redis 缓存**：Key=tokenId, Value=Token JSON, TTL=1h
- **缓存命中**：刷新 issueTime 和 expiryTime，重新写入 Redis
- **缓存未命中**：构建新 Token JSON 写入 Redis

### 4.2 Header 变换

| 阶段 | Cookie | sessionId | userId | tokenId |
|------|--------|-----------|--------|---------|
| 入站请求 | ✓ | ✓ | ✓ | - |
| 转发请求 | ✗ (移除) | ✓ | ✓ | ✓ (注入) |

Cookie 不暴露给 Agent，通过 tokenId 间接引用。Agent 可通过 Portal Cookie 接口 `GET /liveeda/public/cookie?tokenId={tokenId}` 反查 Cookie。

---

## 5. 动态路由

### 5.1 AgentCardService 查询

通过注入的 `AgentCardService.getAgentCardById(agentId)` 查询 agent 注册的 URL，替代原来固定转发到 `gatewayConfig.agentUrl` 的方式。`AgentCardService` 内部使用 Caffeine 缓存。

### 5.2 查找失败处理

当 agent 不存在或 URL 为空时，返回 HTTP 500 + SSE 格式错误事件：

```
HTTP/1.1 500 Internal Server Error
Content-Type: text/event-stream
Cache-Control: no-cache

data: {"jsonrpc":"2.0","id":null,"error":{"code":-32001,"message":"Agent not found or URL is empty","data":{"agentId":"xxx"}}}
```

### 5.3 HTTPS 出站

根据 agent.url 的 scheme 自动判断：
- `http://` → 直接 HttpClientCodec
- `https://` → 在 HttpClientCodec 前注入 `SslContextBuilder.forClient().build()`
- 端口：URI 显式指定则使用，否则 http→80, https→443

---

## 6. SSE 响应透传

### 6.1 组件职责

| 组件 | 职责 |
|------|------|
| `H2ABackendHandler` | 接收 Agent 响应，区分 HttpResponse 和 HttpContent，委托给 Relay |
| `H2AResponseRelay` | 将 Agent 响应逐块写回客户端 Channel，处理 Header/Content/Close/Error |

### 6.2 透传规则

- SSE 事件完整透传，不丢失字段
- 首次收到 `HttpResponse` 时发送响应头给客户端
- 后续 `HttpContent` 逐块写回
- 收到 `LastHttpContent` 时关闭出站连接，向客户端发送 EMPTY_LAST_CONTENT 并关闭
- 异常时返回 HTTP 500 错误响应并关闭连接
- Header 过滤：移除 Connection、Transfer-Encoding、Keep-Alive、Proxy-Connection

---

## 7. 策略模块通知

异步 HTTP POST 通知，不阻塞主流程，不关注返回：

```
POST {gatewayConfig.strategyUrl}
Content-Type: application/json

{"agentId":"xxx","cookie":"IDaaS_SSO=xxx"}
```

- 超时：`gatewayConfig.strategyTimeout`（默认 5000ms）
- 策略 URL 未配置时跳过
- 通知失败仅打印 warn 日志，不影响请求处理

---

## 8. 授权流程（Mock 环境）

### 8.1 完整授权时序

```
MockWebBackend         Agent Gateway        MockAgentServer       Policy Center
     │                      │                     │                    │
     │─ POST /h2a/{id} ───→│                     │                    │
     │                      │─ Token+转发 ───────→│                    │
     │                      │                     │─ authorization- ──→│
     │                      │                     │  decisions         │
     │                      │                     │←─ 200/403决策 ─────│
     │                      │                     │                    │
     │                      │←─ SSE:403事件 ──────│                    │
     │←─ SSE:403透传 ──────│                     │                    │
     │                      │                     │─ 轮询auth ────────→│
     │─ conversation- ───────────────────────────────────────────────→│
     │  authorizations      │                     │                    │
     │←─ 授权确认 ───────────────────────────────────────────────────│
     │                      │                     │←─ AUTHORIZED ──────│
```

### 8.2 MockAgentServer 行为

1. 从请求头提取 tokenId
2. 调用 Policy Center `/internal/authorization-decisions` 获取决策
3. 根据 toolId 决定响应码（`crm.customer.query`→200, `crm.customer.delete`→403）
4. 发送 SSE 响应（含决策 JSON + toolId）
5. 403 时轮询 `/internal/conversation-authorizations/status`（2s间隔，最多15次）

### 8.3 MockWebBackend 行为

1. POST `/h2a/{agentId}/agent`，携带 Cookie/sessionId/userId
2. 流式读取 SSE 响应
3. 收到 403 时调用 `/internal/conversation-authorizations` 确认授权

---

## 9. 配置项

```yaml
gateway:
  port: 8080
  authEnable: false
  agentUrl: http://localhost:9002        # 兜底URL(动态路由后主要用agentCard.url)
  strategyUrl: http://localhost:9003/api/delegation/policy
  strategyTimeout: 5000

spring:
  data:
    redis:
      host: localhost
      port: 6379
```

---

## 10. 组件清单

| 文件 | 职责 |
|------|------|
| `H2ARouteHandler.java` | H2A 请求入口：路径解析、AgentCard 查询、Token 颁发、策略通知、出站转发 |
| `H2ABackendHandler.java` | Agent 响应接收，委托给 Relay |
| `H2AResponseRelay.java` | SSE 响应逐块透传回客户端 |
| `AuthHandler.java` | `/h2a/` 路径直接放行，不校验权限 |
| `AgentCardService.java` | 根据 agentId 查询 AgentCard（含 URL），Caffeine 缓存 |
| `GatewayConfig.java` | 配置项：strategyUrl、strategyTimeout 等 |
| `GatewayServer.java` | Pipeline 组装，注入 AgentCardService 到 H2ARouteHandler |
| `MockAgentServer.java` | 测试：模拟 Agent，对接 Policy Center 授权决策+轮询 |
| `MockWebBackend.java` | 测试：模拟 Web Copilot，发起 H2A SSE 请求+授权确认 |

---

## 11. 关键设计决策

| 决策 | 结论 | 原因 |
|------|------|------|
| H2A 路径前缀 | `/h2a/` | 与 `/a2a/` 对称，语义清晰（Human-to-Agent） |
| 独立 Handler | H2ARouteHandler | 人机/机机交互类型不同，完全隔离 |
| Token 结构 | `agentId:userId:sessionId` 拼接 | 验证阶段无需签名编码，简单可解析 |
| Cookie 处理 | 转发时移除，缓存 Redis | Cookie 不暴露给 Agent |
| 动态路由 | 注入 AgentCardService | 最小改动，复用现有缓存 |
| Agent 查找失败 | HTTP 500 + SSE 错误事件 | 明确标识服务端错误，响应体保持 SSE 格式 |
| HTTPS 支持 | 自动判断 scheme，注入 SslContext | Agent URL 可能是 https |
| 策略通知 | 异步不阻塞 | 仅做事件上报，不影响主流程 |
| userPath 为空 | 转发到 agent.url 本身 | 空路径等同访问 agent 根路径 |

---

## 12. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| Redis 不可用 | Token 缓存失败 | 捕获异常，降级策略待定 |
| 策略模块不可用 | 委托事件上报失败 | 不阻塞，异步通知 |
| Agent 不可用 | 出站连接失败 | 关闭客户端连接，日志记录 |
| 并发 SSE 请求 | tokenId 隔离 | tokenId 包含 sessionId，天然隔离 |
| HTTPS 证书问题 | 出站连接失败 | 使用 JDK 默认 cacerts，信任系统证书 |
