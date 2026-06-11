package com.huawei.it.roma.liveeda.policycenter.domain;

import java.time.Instant;

public record AgentUserPolicy(
        String agentId,
        AccessScope accessScope,
        Instant updatedAt) {

    public AgentUserPolicy(String agentId, AccessScope accessScope) {
        this(agentId, accessScope, null);
    }

    public AccessScope effectiveAccessScope() {
        return accessScope == null ? AccessScope.PUBLIC : accessScope;
    }
}
