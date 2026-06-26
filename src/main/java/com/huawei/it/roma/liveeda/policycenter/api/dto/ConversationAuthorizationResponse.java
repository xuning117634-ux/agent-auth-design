package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.huawei.it.roma.liveeda.policycenter.domain.AuthorizationStatus;

public record ConversationAuthorizationResponse(
        AuthorizationStatus status,
        String toolId) {
}
