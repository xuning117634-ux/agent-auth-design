package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.huawei.it.roma.liveeda.policycenter.domain.AuthorizationStatus;

import java.util.List;

public record BatchConversationAuthorizationResponse(
        AuthorizationStatus status,
        int toolCount,
        List<String> toolIds) {
}
