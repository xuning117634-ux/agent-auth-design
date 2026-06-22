package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.huawei.it.roma.liveeda.policycenter.domain.Decision;

public record ToolAuthorizationPrecheckItemResponse(
        String serverName,
        String toolName,
        String toolId,
        Decision decision) {
}
