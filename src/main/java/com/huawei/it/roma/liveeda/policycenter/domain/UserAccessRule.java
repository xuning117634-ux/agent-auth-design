package com.huawei.it.roma.liveeda.policycenter.domain;

import java.time.Instant;

public record UserAccessRule(String agentId, String userId, Instant updatedAt) {

    public UserAccessRule(String agentId, String userId) {
        this(agentId, userId, null);
    }
}
