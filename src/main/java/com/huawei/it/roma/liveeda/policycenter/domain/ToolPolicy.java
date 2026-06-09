package com.huawei.it.roma.liveeda.policycenter.domain;

import java.time.Instant;

public record ToolPolicy(String agentId, String toolId, AuthMode authMode, Instant updatedAt) {

    public ToolPolicy(String agentId, String toolId, AuthMode authMode) {
        this(agentId, toolId, authMode, null);
    }

    public AuthMode effectiveAuthMode() {
        return AuthMode.normalize(authMode);
    }
}
