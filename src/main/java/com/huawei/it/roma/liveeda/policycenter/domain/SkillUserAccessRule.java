package com.huawei.it.roma.liveeda.policycenter.domain;

import java.time.Instant;

public record SkillUserAccessRule(
        String agentId,
        String skillId,
        String userId,
        Instant updatedAt) {

    public SkillUserAccessRule(String agentId, String skillId, String userId) {
        this(agentId, skillId, userId, null);
    }
}
