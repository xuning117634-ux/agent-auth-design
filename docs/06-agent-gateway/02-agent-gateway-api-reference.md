# Agent Gateway 新增 API 参考

> 适用对象：业务后端、Agent 开发人员、前端
> 最后更新：2026-06-11

## 1. 接口总览

| 编号 | 调用方 | 方法 | 路径 | 服务 | 用途 |
| --- | --- | --- | --- | --- | --- |
| 1 | 浏览器/前端 | `POST` | `/h2a/{agentId}/{userPath}` | Agent Gateway | H2A SSE 代理转发 |
| 2 | Agent | `GET` | `/public/cookie` | Portal | 根据 tokenId 查询 cookie |

---

## 2. H2A SSE 代理转发

**调用方：** 浏览器/前端（通过 Agent Gateway）

```http
POST /h2a/{agentId}/{userPath}
```

### 2.1 请求

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `agentId` | string | 是 | 目标 Agent 的唯一标识，对应 AgentCardService 中注册的 agentId |
| `userPath` | string | 否 | 转发到 Agent 的路径，支持多级路径如 `/a/b/c`。缺省时转发到 `/` |

**请求头：**

| Header | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `Cookie` | string | 是 | 用户认证 Cookie |
| `sessionId` | string | 是 | 当前会话 ID，用于生成 tokenId |
| `userId` | string | 否 | 用户 ID，用于生成 tokenId |
| `Content-Type` | string | 是 | 请求体类型，通常为 `application/json` |
| `Accept` | string | 否 | 期望响应类型，建议 `text/event-stream` |

**tokenId 生成规则：**

```
tokenId = {agentId}:{userId}:{sessionId}
```

Gateway 收到请求后自动生成 tokenId，写入 Redis（TTL 1 小时），并将 tokenId 注入转发请求的 `tokenId` 头中，同时移除原始 `Cookie` 头。

### 2.2 成功响应

请求被转发到 Agent 后，Agent 返回的 SSE 响应原样回传给客户端。

**HTTP 200：** 代理转发成功，SSE 流式响应。

### 2.3 错误响应

#### Agent 未找到（HTTP 500）

当 `agentId` 在 AgentCardService 中找不到对应注册信息时：

```
HTTP/1.1 500 Internal Server Error
Content-Type: text/event-stream

data: {"jsonrpc":"2.0","id":null,"error":{"code":-32001,"message":"Agent not found or URL is empty","data":{"agentId":"xxx"}}}
```

#### 缺少 sessionId（连接关闭）

当请求头中缺少 `sessionId` 时，Gateway 直接关闭连接，无响应体。

---

## 3. 根据 tokenId 查询 Cookie

**调用方：** Agent（通过 Portal）

```http
GET /public/cookie?tokenId={tokenId}
```

### 3.1 请求

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `tokenId` | string | 是 | Gateway 生成的 tokenId，格式为 `{agentId}:{userId}:{sessionId}` |

### 3.2 成功响应

```json
{
  "status": "success",
  "cookie": "IDaaS_SSO=mock_cookie_value"
}
```

### 3.3 Token 不存在

```json
{
  "status": "not_found",
  "cookie": ""
}
```

### 3.4 服务异常

```json
{
  "status": "error",
  "cookie": ""
}
```
