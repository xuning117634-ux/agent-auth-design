package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.domain.Decision;

public record ToolAuthorizationRequiredTool(
        String serverName,
        String toolName,
        String toolId,
        Decision decision) {
}
