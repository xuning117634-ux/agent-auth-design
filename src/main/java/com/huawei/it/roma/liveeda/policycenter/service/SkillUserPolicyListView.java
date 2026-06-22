package com.huawei.it.roma.liveeda.policycenter.service;

import java.time.Instant;
import java.util.List;

public record SkillUserPolicyListView(
        String agentId,
        List<SkillUserPolicyView> skills,
        Instant updatedAt) {
}
