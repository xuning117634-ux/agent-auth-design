package com.huawei.it.roma.liveeda.policycenter.service;

import java.time.Instant;

public record UserPolicySaveResult(
        String agentId,
        int agentUserRuleCount,
        int toolUserRuleCount,
        Instant updatedAt) {
}
