package com.huawei.it.roma.liveeda.policycenter.domain;

import java.time.Instant;

public record ToolUserAccessRule(
        String agentId,
        String toolId,
        String userId,
        Instant updatedAt) {

    public ToolUserAccessRule(String agentId, String toolId, String userId) {
        this(agentId, toolId, userId, null);
    }
}
