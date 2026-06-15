package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.domain.AuthorizationStatus;

import java.util.List;

public record BatchConversationAuthorizationResult(
        AuthorizationStatus status,
        String tokenId,
        int toolCount,
        List<String> toolIds) {
}
