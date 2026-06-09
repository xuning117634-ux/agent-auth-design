# ADR-001：V1 使用明文组合 tokenId

> 状态：已接受
> 负责人：项目维护者
> 决策日期：2026-06-09
> 适用版本：V1
> 最后更新：2026-06-09
> 阅读顺序：03-01

## 背景

下游组件需要携带一个稳定标识，关联 Agent、用户和业务对话。`conversationId` 由业务后端提供，不同 Agent 之间可能重复。

## 决策

V1 使用：

```text
tokenId = agentId:userId:conversationId
```

- `agentId` 必须全局唯一。
- 三个字段必须非空且不得包含 `:`。
- Agent 网关生成 tokenId，Agent 和 MCP 网关只透传。
- 策略中心可以解析 tokenId，但不得把它当作签名身份凭证。

## 后果

- `agentId` 前缀消除不同 Agent 对话 ID 重复造成的冲突。
- 格式简单，便于 V1 调试和授权关联。
- tokenId 可读、可伪造，内部接口仍需要独立认证和调用方信任。
- 未来可以迁移到随机 tokenId 和独立上下文映射，接口仍保持 `tokenId + toolId`。
