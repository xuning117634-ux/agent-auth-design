package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;

import java.time.Instant;

public record ToolPolicyItemResponse(String toolId, AuthMode authMode, Instant updatedAt) {
}
