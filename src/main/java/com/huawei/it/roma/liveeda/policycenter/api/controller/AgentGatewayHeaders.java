package com.huawei.it.roma.liveeda.policycenter.api.controller;

final class AgentGatewayHeaders {

    static final String ACCESS_TOKEN = "X-AGW-ACCESS-TOKEN";
    static final String LEGACY_TOKEN_ID = "tokenid";

    private AgentGatewayHeaders() {
    }

    static String resolveTokenId(String accessToken, String legacyTokenId) {
        if (accessToken != null && !accessToken.isBlank()) {
            return accessToken;
        }
        return legacyTokenId;
    }
}
