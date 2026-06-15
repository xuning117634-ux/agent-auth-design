package com.huawei.it.roma.liveeda.policycenter.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentAccessDecisionRequest(@NotBlank String agentId, @NotBlank String userId) {
}
