package com.huawei.it.roma.liveeda.policycenter.domain;

import java.time.Instant;

public record SkillUserPolicy(
        String agentId,
        String skillId,
        AccessScope accessScope,
        Instant updatedAt) {

    public SkillUserPolicy(String agentId, String skillId, AccessScope accessScope) {
        this(agentId, skillId, accessScope, null);
    }

    public AccessScope effectiveAccessScope() {
        return accessScope == null ? AccessScope.PUBLIC : accessScope;
    }
}
