# 权限策略中心验收场景

> 状态：V1 验收基线
> 负责人：项目维护者
> 适用版本：V1
> 最后更新：2026-06-11
> 阅读顺序：02-04
> 用途：用于生成单元测试、存储集成测试和端到端测试。

## 运行时决策

### 场景 1：工具未绑定

```gherkin
Given Agent A 未绑定 Tool X
When MCP 网关提交 Agent A 对应的 tokenId 和 Tool X
Then 策略中心返回 DENY
And reason 为 TOOL_NOT_BOUND
And 不查询当前对话授权 Redis
And 不触发用户授权页面
```

### 场景 2：无需授权工具

```gherkin
Given Agent A 已绑定 Tool X
And authMode 为 NO_AUTH_REQUIRED
When MCP 网关提交有效 tokenId 和 Tool X
Then 策略中心返回 ALLOW
And reason 为 NO_AUTH_REQUIRED
And 不查询当前对话授权 Redis
```

### 场景 3：需要授权且缓存命中

```gherkin
Given Agent A 已绑定 Tool X
And authMode 为 USER_AUTH_REQUIRED
And authz:{tokenId}:Tool-X 已存在
When MCP 网关提交 tokenId 和 Tool X
Then 策略中心返回 ALLOW
And reason 为 CONVERSATION_AUTHORIZED
```

### 场景 4：需要授权且缓存未命中

```gherkin
Given Agent A 已绑定 Tool X
And authMode 为 USER_AUTH_REQUIRED
And authz:{tokenId}:Tool-X 不存在
When MCP 网关提交 tokenId 和 Tool X
Then 策略中心返回 AUTHORIZATION_REQUIRED
And reason 为 USER_AUTHORIZATION_REQUIRED
```

### 场景 5：历史空标签

```gherkin
Given Agent A 已绑定 Tool X
And authMode 为 null
When MCP 网关提交有效 tokenId 和 Tool X
Then 策略中心按 USER_AUTH_REQUIRED 执行
```

### 场景 5A：每次授权且一次性授权未命中

```gherkin
Given Agent A 已绑定 Tool X
And authMode 为 PER_CALL_AUTH_REQUIRED
And authz:{tokenId}:Tool-X 不存在
When MCP 网关提交 tokenId 和 Tool X
Then 策略中心返回 AUTHORIZATION_REQUIRED
And reason 为 PER_CALL_AUTHORIZATION_REQUIRED
```

### 场景 5B：每次授权命中后消费

```gherkin
Given Agent A 已绑定 Tool X
And authMode 为 PER_CALL_AUTH_REQUIRED
And authz:{tokenId}:Tool-X 已存在
When MCP 网关第一次提交 tokenId 和 Tool X
Then 策略中心返回 ALLOW
And reason 为 PER_CALL_AUTHORIZED
And authz:{tokenId}:Tool-X 被删除
When MCP 网关第二次提交相同 tokenId 和 Tool X
Then 策略中心返回 AUTHORIZATION_REQUIRED
And reason 为 PER_CALL_AUTHORIZATION_REQUIRED
```

### 场景 6：非法 tokenId

```gherkin
Given tokenId 缺少字段、存在空字段或无法按三个片段解析
When MCP 网关请求授权决策
Then 策略中心返回 DENY
And reason 为 INVALID_TOKEN_ID
And 不查询数据库或 Redis
```

### 场景 7：策略数据库异常

```gherkin
Given 策略配置数据库不可用
When MCP 网关请求授权决策
Then 策略中心返回 DENY
And reason 为 POLICY_STORE_UNAVAILABLE
And 不返回 AUTHORIZATION_REQUIRED
```

### 场景 8：Redis 查询异常

```gherkin
Given 工具已绑定且需要用户授权
And Redis 查询超时或失败
When MCP 网关请求授权决策
Then 策略中心返回 DENY
And reason 为 AUTHORIZATION_STORE_UNAVAILABLE
And 不把异常视为授权未命中
```

## 管理员配置

### 场景 9：整份保存

```gherkin
Given Agent A 已绑定 Tool A 和 Tool B
When 管理员整份提交 Tool B 和 Tool C
Then Tool A 被解绑
And Tool B 按请求更新
And Tool C 被新增
And 三项变更在同一事务中提交
```

### 场景 9A：从全量工具中选择部分绑定

```gherkin
Given MCP 网关当前全量工具列表包含 Tool A、Tool B 和 Tool C
When 管理员为 Agent A 只提交 Tool A 和 Tool C
Then 策略中心只保存 Agent A 与 Tool A、Tool C 的绑定
And 不保存 Agent A 与 Tool B 的绑定
```

### 场景 10：空列表解绑全部工具

```gherkin
Given Agent A 已绑定一个或多个工具
When 管理员提交 tools 为空列表
Then Agent A 的全部工具绑定被删除
And 其他 Agent 的配置不受影响
```

### 场景 11：新工具未选择标签

```gherkin
Given 管理员为 Agent A 新增 Tool X
And 请求未提供 authMode
When 策略中心保存配置
Then Tool X 的 authMode 保存为 USER_AUTH_REQUIRED
```

### 场景 12：整份保存回滚

```gherkin
Given 管理员提交一份合法配置
And 数据库在保存过程中失败
When 事务回滚
Then Agent 原有工具策略保持不变
And 接口不返回成功
```

### 场景 13：重复工具

```gherkin
Given 同一请求包含两个相同 toolId
When 管理员保存配置
Then 接口返回 INVALID_REQUEST
And 数据库不发生变化
```

### 场景 13A：未提交工具运行时视为未绑定

```gherkin
Given MCP 网关当前全量工具列表包含 Tool X
And 管理员保存 Agent A 工具策略时未提交 Tool X
When MCP 网关提交 Agent A 对应的 tokenId 和 Tool X
Then 策略中心返回 DENY
And reason 为 TOOL_NOT_BOUND
```

### 场景 13B：再次整份保存减少工具

```gherkin
Given Agent A 已绑定 Tool A 和 Tool B
When 管理员再次整份提交 Tool A
Then Tool B 被解绑
And MCP 网关再次提交 Agent A 对应的 tokenId 和 Tool B 时返回 DENY
And reason 为 TOOL_NOT_BOUND
```

## 人在回路授权

### 场景 14：用户确认授权

```gherkin
Given Tool X 已绑定 Agent A 且需要用户授权
And 业务后端收到有效的用户同意
When 业务后端提交 tokenId 和 Tool X
Then 策略中心写入 authz:{tokenId}:Tool-X
And 授权 Key 的 TTL 为 7 天
And 返回 AUTHORIZED
```

### 场景 14A：用户确认时指定授权有效期

```gherkin
Given Tool X 已绑定 Agent A 且 authMode 为 USER_AUTH_REQUIRED
And 业务后端收到有效的用户同意
When 业务后端提交 tokenId、Tool X 和 expiresInSeconds = 3600
Then 策略中心写入 authz:{tokenId}:Tool-X
And 授权 Key 的 TTL 为 3600 秒
And 返回 AUTHORIZED
```

### 场景 14B：每次授权写入一次性授权

```gherkin
Given Tool X 已绑定 Agent A 且 authMode 为 PER_CALL_AUTH_REQUIRED
And 业务后端收到有效的用户同意
When 业务后端提交 tokenId、Tool X 和 expiresInSeconds = 60
Then 策略中心写入 authz:{tokenId}:Tool-X
And 授权 Key 的 TTL 为 60 秒
And 返回 AUTHORIZED
And 下一次 MCP 网关授权决策命中后会消费该 Key
```

### 场景 14C：授权有效期非法

```gherkin
Given Tool X 已绑定 Agent A 且需要用户授权
When 业务后端提交 expiresInSeconds <= 0
Then 策略中心返回 INVALID_REQUEST
And Redis 不写入授权记录
When 业务后端提交 expiresInSeconds 超过 policy-center.authorization.max-ttl
Then 策略中心返回 INVALID_REQUEST
And Redis 不写入授权记录
```

### 场景 15：重复确认

```gherkin
Given 当前对话已经授权 Tool X
When 业务后端再次提交相同 tokenId 和 Tool X
Then 接口仍返回 AUTHORIZED
And 不产生重复授权语义
```

### 场景 16：轮询后恢复

```gherkin
Given Agent 因 Tool X 未授权而挂起
And 用户在 1 分钟内完成授权
When Agent 下一次轮询授权状态
Then 状态返回 AUTHORIZED
And Agent 恢复检查点
And Agent 重新通过 MCP 网关发起完整授权检查
```

### 场景 17：授权超时

```gherkin
Given Agent 已挂起并每 2 秒轮询
And 用户在 1 分钟内未确认授权
When 等待达到 1 分钟
Then Agent 停止轮询并结束挂起任务
And 策略中心不存在新增授权记录
```

### 场景 18：对无需授权工具提交确认

```gherkin
Given Tool X 的 authMode 为 NO_AUTH_REQUIRED
When 业务后端请求写入当前对话授权
Then 接口返回 AUTHORIZATION_NOT_REQUIRED
And Redis 不写入授权记录
```

### 场景 19：未绑定工具不能通过用户确认放开

```gherkin
Given Tool X 未绑定 Agent A
When 业务后端请求写入当前对话授权
Then 接口返回 TOOL_NOT_BOUND
And Redis 不写入授权记录
```

## 清理、隔离与安全

### 场景 20：对话结束清理

```gherkin
Given tokenId 已授权多个工具
When 业务后端清理该 tokenId
Then 全部 authz:{tokenId}:{toolId} 被删除
And 使用 SCAN MATCH authz:{tokenId}:* 分批清理
And 不使用 Redis KEYS
```

### 场景 21：重复清理

```gherkin
Given tokenId 的授权已经清理
When 再次提交相同清理请求
Then 接口返回成功
And deletedGrantCount 为 0
```

### 场景 22：授权隔离

```gherkin
Given User A 在 Agent A 的 Conversation A 中授权 Tool X
When 使用不同 userId、agentId、conversationId 或 toolId 查询
Then 均不得命中 User A 的原授权
```

### 场景 23：拒绝后不获取 Cookie

```gherkin
Given 策略中心返回 DENY 或 AUTHORIZATION_REQUIRED
When MCP 网关处理决策
Then MCP 网关不向 Agent 网关获取 Cookie
And 不调用 MCP Server
```

### 场景 24：审计信息不含凭证

```gherkin
Given 策略中心完成配置、决策、确认或清理操作
When 写入审计日志
Then 日志包含操作上下文和 traceId
And 不包含 Cookie、业务 Token 或密钥
```

## 人员策略

### 场景 25：PUBLIC Tool 对所有用户开放

```gherkin
Given Agent A 已绑定 Tool X
And Tool X 的 accessScope 为 PUBLIC
And Tool X 保存了不包含 User A 的白名单
When User A 请求 Tool X 的授权决策
Then 策略中心忽略 Tool X 的白名单
And 继续按 Tool X 的 authMode 和 Redis 当前对话授权决策
```

### 场景 26：RESTRICTED Tool 拒绝非白名单用户

```gherkin
Given Agent A 已绑定 Tool X
And Tool X 的 accessScope 为 RESTRICTED
And User A 不在 Tool X 的白名单
When User A 请求 Tool X 的授权决策
Then 策略中心返回 DENY
And reason 为 USER_TOOL_ACCESS_DENIED
And 不进入人在回路授权流程
```

### 场景 27：Agent 访问策略不影响工具决策

```gherkin
Given Agent A 的 accessScope 为 RESTRICTED
And User A 不在 Agent A 的白名单
And Agent A 已绑定 Tool X
And Tool X 的 accessScope 为 PUBLIC
When User A 请求 Tool X 的授权决策
Then 策略中心不检查 Agent 访问策略
And 继续按 Tool X 的 authMode 和 Redis 当前对话授权决策
```

### 场景 28：用户可访问工具列表按 Tool 访问范围过滤

```gherkin
Given Agent A 已绑定 Tool X 和 Tool Y
And Tool X 的 accessScope 为 PUBLIC
And Tool Y 的 accessScope 为 RESTRICTED
And Tool Y 的白名单只包含 User B
When 查询 User A 可访问工具
Then 查询 User A 可访问工具只返回 Tool X
```

### 场景 29：批量工号输入展开并去重

```gherkin
Given 管理员在 Agent 或 Tool 白名单输入框中提交 "z123,c456;z123"
When 策略中心整份保存人员策略
Then 策略中心保存 z123 和 c456 两条白名单记录
And 查询人员策略时返回两个独立用户
```

## 暂缓验收项

以下场景待对应 `TBD` 决策完成后补充：

- 内部接口身份认证与未授权调用。
- 管理员对 Agent 的管理权限校验。
- 用户确认凭证伪造、重放和 1 分钟服务端会话校验。
- 对话结束与迟到授权确认并发的更强仲裁机制。
