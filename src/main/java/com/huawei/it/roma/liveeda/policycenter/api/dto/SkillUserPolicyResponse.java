package com.huawei.it.roma.liveeda.policycenter.api.dto;

import java.time.Instant;
import java.util.List;

public record SkillUserPolicyResponse(
        String agentId,
        List<SkillUserPolicyItemResponse> skills,
        Instant updatedAt) {
}
