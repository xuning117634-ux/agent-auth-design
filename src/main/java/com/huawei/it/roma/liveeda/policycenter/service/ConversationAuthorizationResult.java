package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.domain.AuthorizationStatus;

public record ConversationAuthorizationResult(AuthorizationStatus status, String tokenId, String toolId) {
}
