package com.huawei.it.roma.liveeda.policycenter.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthorizationDecisionRequest(
        @NotBlank String tokenId,
        @NotBlank String toolId) {
}
