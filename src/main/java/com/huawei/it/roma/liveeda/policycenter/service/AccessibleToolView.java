package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;

public record AccessibleToolView(
        String serverName,
        String toolName,
        String toolId,
        AuthMode authMode) {
}
