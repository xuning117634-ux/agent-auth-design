package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import jakarta.validation.constraints.NotBlank;

public record ToolPolicyItemRequest(
        @NotBlank String toolId,
        AuthMode authMode) {
}
