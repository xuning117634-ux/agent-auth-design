package com.huawei.it.roma.liveeda.policycenter.domain;

public record TokenId(String raw, String agentId, String userId, String conversationId) {

    public static TokenId parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidTokenIdException("tokenId must not be blank");
        }

        String[] parts = raw.split(":", -1);
        if (parts.length != 3) {
            throw new InvalidTokenIdException("tokenId must contain agentId, userId and conversationId");
        }
        if (parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new InvalidTokenIdException("tokenId segments must not be blank");
        }

        return new TokenId(raw, parts[0], parts[1], parts[2]);
    }
}
