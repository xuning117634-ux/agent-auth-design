package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.huawei.it.roma.liveeda.policycenter.domain.AgentAccessReason;

public record AgentAccessDecisionResponse(
        String agentId,
        String userId,
        boolean allowed,
        AgentAccessReason reason) {
}
