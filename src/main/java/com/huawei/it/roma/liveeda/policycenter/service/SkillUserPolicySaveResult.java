package com.huawei.it.roma.liveeda.policycenter.service;

import java.time.Instant;

public record SkillUserPolicySaveResult(
        String agentId,
        int skillPolicyCount,
        int skillUserRuleCount,
        Instant updatedAt) {
}
