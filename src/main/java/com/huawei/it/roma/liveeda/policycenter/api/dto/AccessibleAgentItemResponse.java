package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.huawei.it.roma.liveeda.policycenter.domain.AgentAccessReason;

public record AccessibleAgentItemResponse(String agentId, AgentAccessReason reason) {
}
