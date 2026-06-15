package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;

public record AccessibleToolItemResponse(String toolId, AuthMode authMode) {
}
