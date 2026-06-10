package com.huawei.it.roma.liveeda.policycenter.domain;

public record TokenId(String raw, String agentId, String userId, String conversationId) {

    public static TokenId of(String agentId, String userId, String conversationId) {
        validateSegment("agentId", agentId);
        validateSegment("userId", userId);
        validateSegment("conversationId", conversationId);
        return new TokenId(agentId + ":" + userId + ":" + conversationId, agentId, userId, conversationId);
    }

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

    private static void validateSegment(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidTokenIdException(fieldName + " must not be blank");
        }
        if (value.contains(":")) {
            throw new InvalidTokenIdException(fieldName + " must not contain ':'");
        }
    }
}
