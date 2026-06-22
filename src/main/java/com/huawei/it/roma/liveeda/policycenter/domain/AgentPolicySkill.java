package com.huawei.it.roma.liveeda.policycenter.domain;

public record AgentPolicySkill(
        String agentId,
        String skillId,
        String skillName,
        String label,
        String description) {
}
