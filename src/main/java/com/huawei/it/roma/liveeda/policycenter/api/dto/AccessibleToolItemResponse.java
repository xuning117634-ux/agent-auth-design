package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;

public record AccessibleToolItemResponse(
        String serverId,
        String serverName,
        String toolName,
        String toolId,
        AuthMode authMode) {
}
