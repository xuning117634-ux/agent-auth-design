package com.huawei.it.roma.liveeda.policycenter.domain;

import java.time.Instant;

public record ToolUserPolicy(
        String agentId,
        String toolId,
        AccessScope accessScope,
        Instant updatedAt) {

    public ToolUserPolicy(String agentId, String toolId, AccessScope accessScope) {
        this(agentId, toolId, accessScope, null);
    }

    public AccessScope effectiveAccessScope() {
        return accessScope == null ? AccessScope.PUBLIC : accessScope;
    }
}
