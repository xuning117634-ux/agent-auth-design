package com.huawei.it.roma.liveeda.policycenter.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ExternalCleanupAuthorizationRequest(
        @NotBlank String agentId,
        @NotBlank String userId,
        @NotBlank String conversationId) {
}
